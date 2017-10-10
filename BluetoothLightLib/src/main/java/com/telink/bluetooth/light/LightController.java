/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

import com.telink.bluetooth.Command;
import com.telink.bluetooth.Peripheral;
import com.telink.bluetooth.TelinkLog;
import com.telink.crypto.AES;
import com.telink.util.Arrays;
import com.telink.util.Event;
import com.telink.util.EventBus;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public final class LightController extends EventBus<Integer> implements LightPeripheral.Callback {

    /********************************************************************************
     * Command Tag
     *******************************************************************************/

    private static final int TAG_LOGIN_WRITE = 1;
    private static final int TAG_LOGIN_READ = 2;

    private static final int TAG_RESET_MESH_NAME = 101;
    private static final int TAG_RESET_MESH_PASSWORD = 102;
    private static final int TAG_RESET_MESH_LTK = 103;
    private static final int TAG_RESET_MESH_CHECK = 104;
    private static final int TAG_RESET_MESH_ADDRESS = 105;
    private static final int TAG_RESET_MESH_ADDRESS_NOTIFY_ENABLE = 106;
    private static final int TAG_RESET_MESH_ADDRESS_NOTIFY_DATA = 107;

    private static final int TAG_NOTIFY_ENABLE = 201;
    private static final int TAG_NOTIFY_DISABLE = 203;
    private static final int TAG_NOTIFY_UPDATE = 204;

    private static final int TAG_GET_LTK_WRITE = 301;
    private static final int TAG_GET_LTK_READ = 302;

    private static final int TAG_DELETE_WRITE = 401;
    private static final int TAG_DELETE_READ = 402;

    private static final int TAG_OTA_WRITE = 501;
    private static final int TAG_OTA_LAST = 502;
    private static final int TAG_OTA_READ = 503;
    private static final int TAG_OTA_CHECK = 504;

    private static final int TAG_NORMAL_COMMAND = 1000;

    // 控制指令 默认320ms延时
    private static final int DEFAULT_DELAY_TIME = 320;
    /********************************************************************************
     * Attributes
     *******************************************************************************/

    private final byte[] loginRandm = new byte[8];
    private final byte[] ltkRandm = new byte[8];

    private final Handler mDelayHandler = new Handler();
    private final Runnable mConnectTask = new ConnectionRunnable();
    private final Runnable nCheckRunnable = new NCheckRunnable();
    private final Runnable mAllocAddressTask = new SetDeviceAddressRunnable();
    private final Runnable otaTask = new OtaRunnable();

    private final Command.Callback loginCallback = new LoginCommandCallback();
    private final Command.Callback resetCallback = new ResetCommandCallback();
    private final Command.Callback notifyCallback = new NotifyCommandCallback();
    //    private final Command.Callback ltkCallback = new LtkCommandCallback();
    private final Command.Callback normalCallback = new NormalCommandCallback();
    private final Command.Callback deleteCallback = new DeleteCommandCallback();
    private final Command.Callback otaCallback = new OtaCommandCallback();
    private final Command.Callback firmwareCallback = new FirmwareCallback();
    private final OtaPacketParser otaPacketParser = new OtaPacketParser();

    private LightPeripheral light;
    private byte[] sessionKey;
    private int sequenceNumber = Integer.MAX_VALUE;
    private Random random = new SecureRandom();
    private boolean isLogin = false;
    private byte[] meshName;
    private byte[] password;
    private byte[] longTermKey;
    private byte[] newLongTermKey;
    private byte[] newMeshName;
    private byte[] newPassword;
    private Context mContext;
    private int timeoutSeconds = 15;

    // android N check
    private boolean isChecking = false;
    private static final int N_TIMEOUT = 5;
    private int failCount = 0;
    private boolean mIsUpdatingMesh = false;
    private static final int MAX_RETRY = 3;
    //private boolean otaCompleted;

    /********************************************************************************
     * Construct
     *******************************************************************************/

    public LightController() {
    }

    /********************************************************************************
     * Override API
     *******************************************************************************/

    @Override
    public void dispatchEvent(Event<Integer> event) {
        super.dispatchEvent(event.setThreadMode(Event.ThreadMode.Background));
    }

    /********************************************************************************
     * Public API
     *******************************************************************************/

    public LightPeripheral getCurrentLight() {
        return this.light;
    }

    public boolean isLogin() {
        synchronized (this) {
            return this.isLogin;
        }
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds > 0) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }


    public void setIsUpdating(boolean isUpdating) {
        this.mIsUpdatingMesh = isUpdating;
    }


    synchronized public void connect(Context context, LightPeripheral light) {
        failCount = 0;
        this.sequenceNumber = Integer.MAX_VALUE;
        this.mContext = context;
        this.light = light;

        this.light.disconnect();
        this.light.connect(this.mContext, this);

        this.mDelayHandler.removeCallbacks(this.mConnectTask);
        this.mDelayHandler.removeCallbacksAndMessages(null);

        if (this.timeoutSeconds > 0) {
            this.mDelayHandler.postDelayed(this.mConnectTask, this.timeoutSeconds * 1000);
        }
    }

    synchronized public void retryConnect() {

        TelinkLog.d("LightController.retryConnect：" + failCount);
        this.light.connect(this.mContext, this);
        this.mDelayHandler.removeCallbacks(this.mConnectTask);
        this.mDelayHandler.removeCallbacksAndMessages(null);
    }

    synchronized public void disconnect() {
        synchronized (this) {
            this.isLogin = false;
        }

        this.mDelayHandler.removeCallbacks(this.mConnectTask);
        this.mDelayHandler.removeCallbacksAndMessages(null);
        this.resetOta();

        if (this.light != null) {
            TelinkLog.d("LightController.disconnect:" + light.getDeviceName() + "--" + light.getMacAddress());
            this.light.disconnect();
        }

        this.sessionKey = null;
        this.sequenceNumber = 0;

        this.meshName = null;
        this.password = null;
        this.newMeshName = null;
        this.newPassword = null;
    }

    public void login(byte[] meshName, byte[] password) {

        this.meshName = meshName;
        this.password = password;

        if (!AES.Security) {
            synchronized (this) {
                this.isLogin = true;
            }

            this.dispatchEvent(new LightEvent(LightEvent.LOGIN_SUCCESS));
            return;
        }

        byte[] plaintext = new byte[16];

        for (int i = 0; i < 16; i++) {
            plaintext[i] = (byte) (this.meshName[i] ^ this.password[i]);
        }

        byte[] randm = this.generateRandom(this.loginRandm);
        byte[] sk = new byte[16];

        System.arraycopy(randm, 0, sk, 0, randm.length);

        byte[] encrypted;

        try {
            encrypted = AES.encrypt(sk, plaintext);
        } catch (InvalidKeyException | NoSuchAlgorithmException
                | NoSuchPaddingException | UnsupportedEncodingException
                | IllegalBlockSizeException | BadPaddingException
                | NoSuchProviderException e) {
            this.disconnect();
            this.dispatchEvent(new LightEvent(LightEvent.LOGIN_FAILURE, e.getMessage()));
            return;
        }

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.PAIR);

        byte[] commandData = new byte[17];

        commandData[0] = Opcode.BLE_GATT_OP_PAIR_ENC_REQ.getValue();

        System.arraycopy(randm, 0, commandData, 1, randm.length);
        System.arraycopy(encrypted, 8, commandData, 9, 8);
        Arrays.reverse(commandData, 9, 16);

        Command wCmd = Command.newInstance();
        wCmd.type = Command.CommandType.WRITE;
        wCmd.data = commandData;
        wCmd.serviceUUID = serviceUUID;
        wCmd.characteristicUUID = characteristicUUID;
        wCmd.tag = TAG_LOGIN_WRITE;

        Command rCmd = Command.newInstance();
        rCmd.type = Command.CommandType.READ;
        rCmd.serviceUUID = serviceUUID;
        rCmd.characteristicUUID = characteristicUUID;
        rCmd.tag = TAG_LOGIN_READ;

        this.sendCommand(this.loginCallback, wCmd);
        this.sendCommand(this.loginCallback, rCmd);
    }

    public void reset(byte[] meshName, byte[] password, byte[] longTermKey) {
        TelinkLog.d("prepare update mesh info");

        synchronized (this) {
            if (!this.isLogin) {
                this.dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, "not login"));
                return;
            }
        }

        this.light.meshChanged = false;

        this.newMeshName = meshName;
        this.newPassword = password;

        if (longTermKey == null) {
            longTermKey = Manufacture.getDefault().getFactoryLtk();
        }

        this.newLongTermKey = longTermKey;

        if (this.resetDeviceAddress())
            return;

        this.mDelayHandler.removeCallbacksAndMessages(null);

        byte[] nn;
        byte[] pwd;
        byte[] ltk;

        try {
            nn = AES.encrypt(this.sessionKey, meshName);
            pwd = AES.encrypt(this.sessionKey, password);
            ltk = AES.encrypt(this.sessionKey, longTermKey);

            Arrays.reverse(nn, 0, nn.length - 1);
            Arrays.reverse(pwd, 0, pwd.length - 1);
            Arrays.reverse(ltk, 0, ltk.length - 1);

        } catch (InvalidKeyException | NoSuchAlgorithmException
                | NoSuchPaddingException | UnsupportedEncodingException
                | IllegalBlockSizeException | BadPaddingException
                | NoSuchProviderException e) {
            this.dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, e.getMessage()));
            return;
        }

        byte[] nnData = new byte[17];
        nnData[0] = Opcode.BLE_GATT_OP_PAIR_NETWORK_NAME.getValue();
        System.arraycopy(nn, 0, nnData, 1, nn.length);

        byte[] pwdData = new byte[17];
        pwdData[0] = Opcode.BLE_GATT_OP_PAIR_PASS.getValue();
        System.arraycopy(pwd, 0, pwdData, 1, pwd.length);

        byte[] ltkData = new byte[17];
        ltkData[0] = Opcode.BLE_GATT_OP_PAIR_LTK.getValue();
        System.arraycopy(ltk, 0, ltkData, 1, ltk.length);

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID pairUUID = manufacture.getUUID(Manufacture.UUIDType.PAIR);

        Command nnCmd = new Command(serviceUUID,
                pairUUID, Command.CommandType.WRITE,
                nnData, TAG_RESET_MESH_NAME);
        Command pwdCmd = new Command(serviceUUID, pairUUID, Command.CommandType.WRITE,
                pwdData, TAG_RESET_MESH_PASSWORD);
        Command ltkCmd = new Command(serviceUUID, pairUUID, Command.CommandType.WRITE,
                ltkData, TAG_RESET_MESH_LTK);
        Command checkCmd = new Command(serviceUUID, pairUUID, Command.CommandType.READ,
                null, TAG_RESET_MESH_CHECK);

        this.sendCommand(this.resetCallback, nnCmd);
        this.sendCommand(this.resetCallback, pwdCmd);
        this.sendCommand(this.resetCallback, ltkCmd);
        this.sendCommand(this.resetCallback, checkCmd);
    }

    protected boolean resetDeviceAddress() {

        int newAddress = this.light.getNewMeshAddress();
        int oldAddress = this.light.getMeshAddress();

        TelinkLog.d("mesh address -->" + newAddress + " : " + oldAddress);

        if (newAddress == oldAddress)
            return false;

        this.enableNotification(this.notifyCallback, TAG_RESET_MESH_ADDRESS_NOTIFY_DATA);
        byte opcode = (byte) 0xE0;
        byte[] params = new byte[]{(byte) (newAddress & 0xFF), (byte) (newAddress >> 8 & 0xFF)};

        TelinkLog.d("prepare update mesh address -->" + light.getMacAddress() + " src : " + Integer.toHexString(oldAddress) + " new : " + Integer.toHexString(newAddress));

        this.sendCommand(this.normalCallback, opcode, 0x0000, params, false, TAG_RESET_MESH_ADDRESS, 0);
        byte[] params1 = new byte[]{(byte) 0xFF, (byte) 0xFF};
        this.sendCommand(this.normalCallback, opcode, 0x0000, params1, false, TAG_NORMAL_COMMAND, 0);
        this.mDelayHandler.postDelayed(this.mAllocAddressTask, 3000);

        return true;
    }

    /********************************************************************************
     * Notify API
     *******************************************************************************/

    private void enableNotification(Command.Callback callback, Object tag) {
        synchronized (this) {
            if (!this.isLogin)
                return;
        }

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.NOTIFY);

        Command enableNotifyCmd = Command.newInstance();
        enableNotifyCmd.type = Command.CommandType.ENABLE_NOTIFY;
        enableNotifyCmd.serviceUUID = serviceUUID;
        enableNotifyCmd.characteristicUUID = characteristicUUID;
        enableNotifyCmd.tag = tag;

        this.sendCommand(callback, enableNotifyCmd);
    }

    public void enableNotification() {
        this.enableNotification(this.notifyCallback, TAG_NOTIFY_ENABLE);
    }

    public void disableNotification() {

        synchronized (this) {
            if (!this.isLogin)
                return;
        }

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.NOTIFY);

        Command disableNotifyCmd = Command.newInstance();
        disableNotifyCmd.type = Command.CommandType.DISABLE_NOTIFY;
        disableNotifyCmd.serviceUUID = serviceUUID;
        disableNotifyCmd.characteristicUUID = characteristicUUID;
        disableNotifyCmd.tag = TAG_NOTIFY_DISABLE;

        this.sendCommand(this.notifyCallback, disableNotifyCmd);
    }

    public void updateNotification(byte[] data) {

        synchronized (this) {
            if (!this.isLogin)
                return;
        }

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.NOTIFY);

        Command updateNotifyCmd = Command.newInstance();
        updateNotifyCmd.type = Command.CommandType.WRITE;
        updateNotifyCmd.data = data;
        updateNotifyCmd.serviceUUID = serviceUUID;
        updateNotifyCmd.characteristicUUID = characteristicUUID;
        updateNotifyCmd.tag = TAG_NOTIFY_UPDATE;
        updateNotifyCmd.delay = DEFAULT_DELAY_TIME;

        this.sendCommand(null, updateNotifyCmd);
        TelinkLog.d("LightController#updateNotification");
    }

    public void updateNotification() {
        byte[] data = new byte[]{0x01};
        this.updateNotification(data);
    }

    /********************************************************************************
     * LTK API
     *******************************************************************************/
/*

    public void getLongTermKey() {

        synchronized (this) {
            if (!this.isLogin)
                return;
        }

        byte[] plaintext = new byte[16];

        for (int i = 0; i < 16; i++) {
            plaintext[i] = (byte) (this.meshName[i] ^ this.password[i]);
        }

        byte[] randm = this.generateRandom(this.ltkRandm);
        byte[] sk = new byte[16];

        System.arraycopy(randm, 0, sk, 0, randm.length);

        byte[] encrypted;

        try {
            encrypted = AES.encrypt(sk, plaintext);
        } catch (InvalidKeyException | NoSuchAlgorithmException
                | NoSuchPaddingException | UnsupportedEncodingException
                | IllegalBlockSizeException | BadPaddingException
                | NoSuchProviderException e) {
            this.dispatchEvent(new LightEvent(LightEvent.GET_LTK_FAILURE, e.getMessage()));
            return;
        }

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.PAIR);

        byte[] commandData = new byte[17];

        commandData[0] = Opcode.BLE_GATT_OP_PAIR_LTK_REQ.getValue();

        System.arraycopy(randm, 0, commandData, 1, randm.length);
        System.arraycopy(encrypted, 8, commandData, 9, 8);

        Arrays.reverse(commandData, 9, 16);

        Command wCmd = Command.newInstance();
        wCmd.serviceUUID = serviceUUID;
        wCmd.characteristicUUID = characteristicUUID;
        wCmd.type = Command.CommandType.WRITE;
        wCmd.data = commandData;
        wCmd.tag = TAG_GET_LTK_WRITE;

        Command rCmd = Command.newInstance();
        rCmd.serviceUUID = serviceUUID;
        rCmd.characteristicUUID = characteristicUUID;
        rCmd.type = Command.CommandType.READ;
        rCmd.tag = TAG_GET_LTK_READ;

        this.sendCommand(this.ltkCallback, wCmd);
        this.sendCommand(this.ltkCallback, rCmd);
    }
*/

    /********************************************************************************
     * Delete API
     *******************************************************************************/

    public void delete() {

        synchronized (this) {
            if (!this.isLogin) {
                this.dispatchEvent(new LightEvent(LightEvent.DELETE_FAILURE, "not login"));
                return;
            }
        }

        byte[] plaintext = new byte[16];

        for (int i = 0; i < 16; i++) {
            plaintext[i] = (byte) (this.meshName[i] ^ this.password[i]);
        }

        byte[] randm = new byte[8];
        byte[] sk = new byte[16];

        this.generateRandom(randm);

        System.arraycopy(randm, 0, sk, 0, randm.length);

        byte[] encrypted;

        try {
            encrypted = AES.encrypt(sk, plaintext);
        } catch (InvalidKeyException | NoSuchAlgorithmException
                | NoSuchPaddingException | UnsupportedEncodingException
                | IllegalBlockSizeException | BadPaddingException
                | NoSuchProviderException e) {
            this.dispatchEvent(new LightEvent(LightEvent.DELETE_FAILURE, e.getMessage()));
            return;
        }

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.PAIR);

        byte[] commandData = new byte[17];

        commandData[0] = Opcode.BLE_GATT_OP_PAIR_DELETE.getValue();

        System.arraycopy(randm, 0, commandData, 1, randm.length);
        System.arraycopy(encrypted, 8, commandData, 9, 8);

        Arrays.reverse(commandData, 9, 16);

        Command wCmd = Command.newInstance();
        wCmd.serviceUUID = serviceUUID;
        wCmd.characteristicUUID = characteristicUUID;
        wCmd.type = Command.CommandType.WRITE;
        wCmd.data = commandData;
        wCmd.tag = TAG_DELETE_WRITE;

        Command rCmd = Command.newInstance();
        rCmd.serviceUUID = serviceUUID;
        rCmd.characteristicUUID = characteristicUUID;
        rCmd.type = Command.CommandType.READ;
        rCmd.tag = TAG_DELETE_READ;

        this.sendCommand(this.deleteCallback, wCmd);
        this.sendCommand(this.deleteCallback, rCmd);
    }

    /********************************************************************************
     * OTA API
     *******************************************************************************/

    public void startOta(byte[] firmware) {

        synchronized (this) {
            //this.otaCompleted = false;
            if (!this.isLogin) {
                this.dispatchEvent(new LightEvent(LightEvent.OTA_FAILURE, "not login"));
                return;
            }
        }

        TelinkLog.d("Start OTA");
        this.resetOta();
        this.otaPacketParser.set(firmware);
        this.sendNextOtaPacketCommand();
    }

    public int getOtaProgress() {
        return this.otaPacketParser.getProgress();
    }

    private void resetOta() {
        this.mDelayHandler.removeCallbacksAndMessages(null);
        this.mDelayHandler.removeCallbacks(otaTask);
        this.otaPacketParser.clear();
    }

    private void setOtaProgressChanged() {
        if (this.otaPacketParser.invalidateProgress()) {
            this.dispatchEvent(new LightEvent(LightEvent.OTA_PROGRESS));
        }
    }

    private boolean sendNextOtaPacketCommand() {
        boolean isLast = false;

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.OTA);

        Command cmd = Command.newInstance();
        cmd.serviceUUID = serviceUUID;
        cmd.characteristicUUID = characteristicUUID;
        cmd.type = Command.CommandType.WRITE_NO_RESPONSE;

        if (this.otaPacketParser.hasNextPacket()) {
            cmd.data = this.otaPacketParser.getNextPacket();
            cmd.tag = TAG_OTA_WRITE;
        } else {
            cmd.data = this.otaPacketParser.getCheckPacket();
            cmd.tag = TAG_OTA_LAST;
            isLast = true;
        }

        this.sendCommand(this.otaCallback, cmd);

        return isLast;
    }

    private boolean validateOta() {
        int sectionSize = Manufacture.getDefault().getOtaSize();
        int sendTotal = otaPacketParser.getNextPacketIndex() * 16;
        TelinkLog.d("ota onCommandSampled byte length : " + sendTotal);
        if (sendTotal > 0 && sendTotal % sectionSize == 0) {
            TelinkLog.d("onCommandSampled ota read packet " + otaPacketParser.getNextPacketIndex());
            Manufacture manufacture = Manufacture.getDefault();
            UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
            UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.OTA);

            Command cmd = Command.newInstance();
            cmd.serviceUUID = serviceUUID;
            cmd.characteristicUUID = characteristicUUID;
            cmd.type = Command.CommandType.READ;
            cmd.tag = TAG_OTA_READ;
            this.sendCommand(otaCallback, cmd);
            return true;
        }

        return false;
    }

    private void sendOtaCheckPacket() {
        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.OTA);

        Command cmd = Command.newInstance();
        cmd.serviceUUID = serviceUUID;
        cmd.characteristicUUID = characteristicUUID;
        cmd.type = Command.CommandType.READ;
        cmd.tag = TAG_OTA_CHECK;
        cmd.delay = 0;
        this.sendCommand(otaCallback, cmd);
    }

    /********************************************************************************
     * Command API
     *******************************************************************************/

    private boolean sendCommand(Command.Callback callback, byte[] commandData, boolean noResponse, Object tag, int delay) {

        byte[] sk = this.sessionKey;
        int sn = this.sequenceNumber;

        TelinkLog.d("LightController#sendCommand#NoEncrypt: " + Arrays.bytesToHexString(commandData, ":"));
        byte[] macAddress = this.light.getMacBytes();
        byte[] nonce = this.getSecIVM(macAddress, sn);
        byte[] data = AES.encrypt(sk, nonce, commandData);

        Manufacture manufacture = Manufacture.getDefault();
        UUID serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE);
        UUID characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.COMMAND);

        Command command = Command.newInstance();
        command.type = noResponse ? Command.CommandType.WRITE_NO_RESPONSE : Command.CommandType.WRITE;
        command.data = data;
        command.serviceUUID = serviceUUID;
        command.characteristicUUID = characteristicUUID;
        command.tag = tag;
        command.delay = delay;

        return this.sendCommand(callback, command);
    }

    private boolean sendCommand(Command.Callback callback, Command cmd) {

        boolean success = true;

        synchronized (this) {
            if (!this.isLogin) {
                success = false;
            }
        }

        this.light.sendCommand(callback, cmd);

        return success;
    }

    public boolean sendCommand(Command.Callback callback, byte opcode, int address, byte[] params, boolean noResponse, Object tag, int delay) {

        int sn = this.generateSequenceNumber();

        byte[] command = new byte[20];

        int offset = 0;

        // SN
        command[offset++] = (byte) (sn & 0xFF);
        command[offset++] = (byte) (sn >> 8 & 0xFF);
        command[offset++] = (byte) (sn >> 16 & 0xFF);

        // src address
        command[offset++] = 0x00;
        command[offset++] = 0x00;

        // dest address
        command[offset++] = (byte) (address & 0xFF);
        command[offset++] = (byte) (address >> 8 & 0xFF);

        // opcode
        command[offset++] = (byte) (opcode | 0xC0);

        Manufacture manufacture = Manufacture.getDefault();
        int vendorId = manufacture.getVendorId();

        // vendor Id
        command[offset++] = (byte) (vendorId >> 8 & 0xFF);
        command[offset++] = (byte) (vendorId & 0xFF);

        // params
        if (params != null) {
            System.arraycopy(params, 0, command, offset, params.length);
        }

        return this.sendCommand(callback, command, noResponse, tag, delay);
    }

    public boolean sendCommand(byte opcode, int address, byte[] params, boolean noResponse, int delay) {
        return this.sendCommand(opcode, address, params, noResponse, TAG_NORMAL_COMMAND, delay);
    }

    public boolean sendCommand(byte opcode, int address, byte[] params, boolean noResponse, Object tag, int delay) {
        return this.sendCommand(this.normalCallback, opcode, address, params, noResponse, tag, delay);
    }

    /********************************************************************************
     * Device Firmware Information
     *******************************************************************************/

    public boolean requestFirmware() {

        UUID serviceUUID = UuidInformation.SERVICE_DEVICE_INFORMATION.getValue();
        UUID characteristicUUID = UuidInformation.CHARACTERISTIC_FIRMWARE.getValue();

        Command cmd = Command.newInstance();
        cmd.serviceUUID = serviceUUID;
        cmd.characteristicUUID = characteristicUUID;
        cmd.type = Command.CommandType.READ;

        return this.sendCommand(this.firmwareCallback, cmd);
    }

    /********************************************************************************
     * Private API
     *******************************************************************************/

    private int generateSequenceNumber() {

        int maxNum = 0xFFFFFF;

        if (this.sequenceNumber > maxNum)
            this.sequenceNumber = Math.round((float) Math.random()
                    * (maxNum - 1)) + 1;

        this.sequenceNumber++;

        return this.sequenceNumber;
    }

    private byte[] generateRandom(byte[] randm) {
        this.random.nextBytes(randm);
        return randm;
    }

    private byte[] getSecIVM(byte[] meshAddress, int sn) {

        byte[] ivm = new byte[8];

        System.arraycopy(meshAddress, 0, ivm, 0, meshAddress.length);

        ivm[4] = 0x01;
        ivm[5] = (byte) (sn & 0xFF);
        ivm[6] = (byte) (sn >> 8 & 0xFF);
        ivm[7] = (byte) (sn >> 16 & 0xFF);

        return ivm;
    }

    private byte[] getSecIVS(byte[] macAddress) {

        byte[] ivs = new byte[8];

        ivs[0] = macAddress[0];
        ivs[1] = macAddress[1];
        ivs[2] = macAddress[2];

        return ivs;
    }

    private byte[] getSessionKey(byte[] meshName, byte[] password,
                                 byte[] randm, byte[] rands, byte[] sk) throws Exception {

        byte[] key = new byte[16];

        System.arraycopy(rands, 0, key, 0, rands.length);

        byte[] plaintext = new byte[16];

        for (int i = 0; i < 16; i++) {
            plaintext[i] = (byte) (meshName[i] ^ password[i]);
        }

        byte[] encrypted = AES.encrypt(key, plaintext);
        byte[] result = new byte[16];

        System.arraycopy(rands, 0, result, 0, rands.length);
        System.arraycopy(encrypted, 8, result, 8, 8);
        Arrays.reverse(result, 8, 15);

        if (!Arrays.equals(result, sk))
            return null;

        System.arraycopy(randm, 0, key, 0, randm.length);
        System.arraycopy(rands, 0, key, 8, rands.length);

        byte[] sessionKey = AES.encrypt(plaintext, key);
        Arrays.reverse(sessionKey, 0, sessionKey.length - 1);

        return sessionKey;
    }

    /********************************************************************************
     * Implements API
     *******************************************************************************/

    @Override
    public void onConnect(LightPeripheral light) {
        TelinkLog.d("LightController#onConnect");
        if (isN() && mIsUpdatingMesh) {
            TelinkLog.d("mDelayHandler#nCheckRunnable");
            mDelayHandler.removeCallbacks(mConnectTask);
            mDelayHandler.postDelayed(nCheckRunnable, N_TIMEOUT * 1000);
            isChecking = false;
        }
    }

    @Override
    public void onDisconnect(LightPeripheral light) {

        TelinkLog.d("LightController.onDisconnect");
        this.disconnect();
        if (isN() && mIsUpdatingMesh && isChecking) {
            isChecking = false;
            mDelayHandler.removeCallbacks(nCheckRunnable);
            failCount++;
            TelinkLog.d("fail count:" + failCount);
            if (failCount >= MAX_RETRY) {
                TelinkLog.d("LightController.onDisconnect.CONNECT_FAILURE_N");
                this.dispatchEvent(new LightEvent(LightEvent.CONNECT_FAILURE_N, " onDisconnect " + light.getMacAddress()));
            } else {
                retryConnect();
            }
        } else {
            this.dispatchEvent(new LightEvent(LightEvent.CONNECT_FAILURE, " onDisconnect " + light.getMacAddress()));
        }
    }

    @Override
    public void onServicesDiscovered(LightPeripheral light,
                                     List<BluetoothGattService> services) {
        if (isN() && mIsUpdatingMesh && services.size() == 0) {
            disconnect();
        } else {
            this.mDelayHandler.removeCallbacks(nCheckRunnable);
            this.dispatchEvent(new LightEvent(LightEvent.CONNECT_SUCCESS));
        }
    }

    @Override
    public void onNotify(LightPeripheral light, byte[] data,
                         UUID serviceUUID, UUID characteristicUUID, Object tag) {

        byte[] macAddress = light.getMacBytes();
        byte[] nonce = getSecIVS(macAddress);
        System.arraycopy(data, 0, nonce, 3, 5);
        byte[] result = AES.decrypt(this.sessionKey, nonce, data);

        TelinkLog.e("Notify Data --> " + Arrays.bytesToHexString(result, ","));

        this.onDeviceAddressNotify(data, tag);
        this.dispatchEvent(new LightEvent(LightEvent.NOTIFICATION_RECEIVE, result));
    }

    private void onDeviceAddressNotify(byte[] data, Object tag) {

        if (!tag.equals(TAG_RESET_MESH_ADDRESS_NOTIFY_DATA))
            return;

        int length = data.length;
        int minLength = 20;
        int position = 7;

        if (length < minLength)
            return;

        int opcode = data[position++] & 0xFF;
        int vendorId = (data[position++] << 8) + data[position++];

        if (vendorId != Manufacture.getDefault().getVendorId())
            return;

        if (opcode != 0xE1)
            return;

        int meshAddress = data[10] + (data[11] << 8);

        if (meshAddress == light.getMeshAddress())
            return;

        light.setMeshAddress(meshAddress);

        TelinkLog.d("Device Address Update Success --> old : " + Integer.toHexString(light.getMeshAddress()) + " new: " + Integer.toHexString(meshAddress));

        this.reset(this.newMeshName, this.newPassword, this.newLongTermKey);
    }

    @Override
    public void onRssiChanged(LightPeripheral light) {
        this.dispatchEvent(new LightEvent(LightEvent.RSSI_CHANGED));
    }

    /********************************************************************************
     * Event Class
     *******************************************************************************/

    public final class LightEvent extends Event<Integer> {

        public static final int LOGIN_SUCCESS = 0;
        public static final int LOGIN_FAILURE = 1;

        public static final int CONNECT_SUCCESS = 3;
        public static final int CONNECT_FAILURE = 4;
        public static final int CONNECT_FAILURE_N = 5;// android N

        public static final int RESET_MESH_SUCCESS = 10;
        public static final int RESET_MESH_FAILURE = 11;

        public static final int ENABLE_NOTIFICATION_SUCCESS = 20;
        public static final int ENABLE_NOTIFICATION_FAILURE = 21;
        public static final int NOTIFICATION_RECEIVE = 22;

        public static final int GET_LTK_SUCCESS = 30;
        public static final int GET_LTK_FAILURE = 31;

        public static final int DELETE_SUCCESS = 40;
        public static final int DELETE_FAILURE = 41;

        public static final int COMMAND_SUCCESS = 50;
        public static final int COMMAND_FAILURE = 51;

        public static final int RSSI_CHANGED = 60;

        public static final int OTA_SUCCESS = 71;
        public static final int OTA_FAILURE = 72;
        public static final int OTA_PROGRESS = 73;

        public static final int GET_FIRMWARE_SUCCESS = 80;
        public static final int GET_FIRMWARE_FAILURE = 81;

        private Object args;

        public LightEvent(Integer type, Object args) {
            super(null, type);
            this.args = args;
        }

        public LightEvent(Integer type) {
            super(null, type);
        }

        public Object getArgs() {
            return args;
        }
    }

    /********************************************************************************
     * Callback Class API
     *******************************************************************************/


    private final class NCheckRunnable implements Runnable {

        @Override
        public void run() {

            synchronized (LightController.this) {
                if (!LightController.this.isLogin) {
                    TelinkLog.d("LightController.Connection Timeout N");
                    disconnect();
                    isChecking = true;
                }
            }
        }
    }


    private final class ConnectionRunnable implements Runnable {

        @Override
        public void run() {

            synchronized (LightController.this) {
                if (!LightController.this.isLogin) {
                    TelinkLog.d("LightController.Connection Timeout");
                    disconnect();
                    LightController.this.dispatchEvent(new LightEvent(LightEvent.CONNECT_FAILURE, "connection timeout"));
                }
            }
        }
    }

    private final class LoginCommandCallback implements Command.Callback {

        @Override
        public void success(Peripheral peripheral, Command command,
                            Object response) {

            if (!command.tag.equals(TAG_LOGIN_READ))
                return;

            byte[] data = (byte[]) response;

            if (data[0] == Opcode.BLE_GATT_OP_PAIR_ENC_FAIL.getValue()) {
                disconnect();
                dispatchEvent(new LightEvent(LightEvent.LOGIN_FAILURE, "encryption is not correct"));
                return;
            }

            byte[] sk = new byte[16];
            byte[] rands = new byte[8];

            System.arraycopy(data, 1, sk, 0, 16);
            System.arraycopy(data, 1, rands, 0, 8);

            try {

                sessionKey = getSessionKey(meshName, password, loginRandm, rands, sk);

                if (sessionKey == null) {
                    disconnect();
                    dispatchEvent(new LightEvent(LightEvent.LOGIN_FAILURE, "sessionKey invalid"));
                    return;
                }

                synchronized (LightController.this) {
                    isLogin = true;
                }

                mDelayHandler.removeCallbacks(mConnectTask);
                mDelayHandler.removeCallbacksAndMessages(null);
                dispatchEvent(new LightEvent(LightEvent.LOGIN_SUCCESS));

            } catch (Exception e) {
                disconnect();
                dispatchEvent(new LightEvent(LightEvent.LOGIN_FAILURE, e.getMessage()));
            }
        }

        @Override
        public void error(Peripheral peripheral, Command command,
                          String reason) {
            TelinkLog.d("login command error  : " + reason);

            disconnect();
            dispatchEvent(new LightEvent(LightEvent.LOGIN_FAILURE, reason));
        }

        @Override
        public boolean timeout(Peripheral peripheral, Command command) {
            return false;
        }
    }

    private final class SetDeviceAddressRunnable implements Runnable {

        @Override
        public void run() {
            dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, "set device address timeout"));
        }
    }

    private final class ResetCommandCallback implements Command.Callback {

        @Override
        public void success(Peripheral peripheral, Command command,
                            Object response) {

            if (!command.tag.equals(TAG_RESET_MESH_CHECK))
                return;

            byte[] data = (byte[]) response;

            if (data[0] == Opcode.BLE_GATT_OP_PAIR_CONFIRM.getValue()) {

                try {

                    byte[] sk = new byte[16];

                    for (int i = 0; i < 16; i++) {
                        sk[i] = (byte) (newMeshName[i] ^ newPassword[i] ^ newLongTermKey[i]);
                    }

                    sk = AES.encrypt(sessionKey, sk);
                    sk = Arrays.reverse(sk);

                    byte[] sk1 = new byte[16];
                    System.arraycopy(data, 1, sk1, 0, 16);

                    if (!Arrays.equals(sk, sk1)) {
                        light.meshChanged = false;
                        dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"));
                        return;
                    }

                } catch (InvalidKeyException | IllegalBlockSizeException
                        | BadPaddingException | NoSuchAlgorithmException
                        | NoSuchPaddingException | NoSuchProviderException | UnsupportedEncodingException e) {
                    light.meshChanged = false;
                    dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"));
                    return;
                }

                meshName = newMeshName;
                password = newPassword;
                longTermKey = newLongTermKey;

                LightPeripheral light = (LightPeripheral) peripheral;
                light.setMeshName(meshName);
                light.setPassword(newPassword);
                light.setLongTermKey(newLongTermKey);
                light.meshChanged = true;
                dispatchEvent(new LightEvent(LightEvent.RESET_MESH_SUCCESS));

            } else {
                light.meshChanged = false;
                dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"));
            }
        }

        @Override
        public void error(Peripheral peripheral, Command command,
                          String reason) {
            dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, reason));
        }

        @Override
        public boolean timeout(Peripheral peripheral, Command command) {
            return false;
        }
    }

    private final class NotifyCommandCallback implements Command.Callback {

        @Override
        public void success(Peripheral peripheral, Command command, Object data) {

        }

        @Override
        public void error(Peripheral peripheral, Command command,
                          String reason) {

            if (command.tag.equals(TAG_RESET_MESH_ADDRESS_NOTIFY_ENABLE) || command.tag.equals(TAG_RESET_MESH_ADDRESS_NOTIFY_DATA)) {
                dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, "set address fail"));
            } else {
                dispatchEvent(new LightEvent(LightEvent.ENABLE_NOTIFICATION_FAILURE, reason));
            }
        }

        @Override
        public boolean timeout(Peripheral peripheral, Command command) {
            return false;
        }
    }

    /*private final class LtkCommandCallback implements Command.Callback {

        @Override
        public void success(Peripheral peripheral, Command command,
                            Object response) {

            if (!command.tag.equals(TAG_GET_LTK_READ))
                return;

            byte[] data = (byte[]) response;

            byte[] sk = new byte[16];

            for (int i = 0; i < 16; i++) {
                sk[i] = (byte) (ltkRandm[i] ^ meshName[i] ^ password[i]);
            }

            byte[] decrypted;

            try {
                decrypted = AES.decrypt(sk, data);
            } catch (InvalidKeyException | IllegalBlockSizeException
                    | BadPaddingException | NoSuchAlgorithmException
                    | NoSuchPaddingException | NoSuchProviderException e) {
                dispatchEvent(new LightEvent(LightEvent.GET_LTK_FAILURE, e.getMessage()));
                return;
            }

            longTermKey = decrypted;
            LightPeripheral light = (LightPeripheral) peripheral;
            light.setLongTermKey(longTermKey);
            dispatchEvent(new LightEvent(LightEvent.GET_LTK_SUCCESS, longTermKey));
        }

        @Override
        public void error(Peripheral peripheral, Command command,
                          String reason) {
            dispatchEvent(new LightEvent(LightEvent.GET_LTK_FAILURE, reason));
        }

        @Override
        public boolean timeout(Peripheral peripheral, Command command) {
            return false;
        }
    }*/

    private final class DeleteCommandCallback implements Command.Callback {

        @Override
        public void success(Peripheral peripheral, Command command, Object object) {
            if (!command.tag.equals(TAG_DELETE_READ))
                return;
            dispatchEvent(new LightEvent(LightEvent.DELETE_SUCCESS));
        }

        @Override
        public void error(Peripheral peripheral, Command command, String reason) {
            dispatchEvent(new LightEvent(LightEvent.DELETE_FAILURE, reason));
        }

        @Override
        public boolean timeout(Peripheral peripheral, Command command) {
            return false;
        }
    }

    private final class OtaCommandCallback implements Command.Callback {

        @Override
        public void success(Peripheral peripheral, Command command, Object obj) {
            if (command.tag.equals(TAG_OTA_WRITE)) {
                int delay = Manufacture.getDefault().getOtaDelay();
                if (delay <= 0) {
                    if (!validateOta())
                        sendNextOtaPacketCommand();
                } else {
                    mDelayHandler.postDelayed(otaTask, delay);
                }
                setOtaProgressChanged();
            } else if (command.tag.equals(TAG_OTA_READ)) {
                TelinkLog.d("read response : " + Arrays.bytesToString((byte[]) obj));
                sendNextOtaPacketCommand();
            } else if (command.tag.equals(TAG_OTA_CHECK)) {
                TelinkLog.d("last read packet response : " + Arrays.bytesToString((byte[]) obj));
                resetOta();
                setOtaProgressChanged();
                /*synchronized (this) {
                    otaCompleted = true;
                }*/
                dispatchEvent(new LightEvent(LightEvent.OTA_SUCCESS));
            } else if (command.tag.equals(TAG_OTA_LAST)) {
                sendOtaCheckPacket();
            }
        }

        @Override
        public void error(Peripheral peripheral, Command command, String errorMsg) {
            TelinkLog.d("error packet : " + Arrays.bytesToHexString(command.data, ":"));
            if (command.tag.equals(TAG_OTA_CHECK)) {
                TelinkLog.d("last read packet response error : ");
                resetOta();
                setOtaProgressChanged();
                /*synchronized (this) {
                    otaCompleted = true;
                }*/
                dispatchEvent(new LightEvent(LightEvent.OTA_SUCCESS));
            } else {
                resetOta();
                dispatchEvent(new LightEvent(LightEvent.OTA_FAILURE));
            }
        }

        @Override
        public boolean timeout(Peripheral peripheral, Command command) {
            if (command.tag.equals(TAG_OTA_CHECK)) {
                TelinkLog.d("last read packet response timeout : ");
                resetOta();
                setOtaProgressChanged();
                /*synchronized (this) {
                    otaCompleted = true;
                }*/
                dispatchEvent(new LightEvent(LightEvent.OTA_SUCCESS));
                return false;
            } else if (command.tag.equals(TAG_OTA_READ)) {
                sendNextOtaPacketCommand();
                return false;
            }
            TelinkLog.d("timeout : " + Arrays.bytesToHexString(command.data, ":"));
            return false;
        }
    }

    private final class FirmwareCallback implements Command.Callback {

        @Override
        public void success(Peripheral peripheral, Command command, Object obj) {
            LightPeripheral light = (LightPeripheral) peripheral;
            light.putCharacteristicValue(command.characteristicUUID, (byte[]) obj);
            dispatchEvent(new LightEvent(LightEvent.GET_FIRMWARE_SUCCESS));
        }

        @Override
        public void error(Peripheral peripheral, Command command, String errorMsg) {
            dispatchEvent(new LightEvent(LightEvent.GET_FIRMWARE_FAILURE));
        }

        @Override
        public boolean timeout(Peripheral peripheral, Command command) {
            return false;
        }
    }

    private final class NormalCommandCallback implements Command.Callback {

        @Override
        public void success(Peripheral peripheral, Command command,
                            Object obj) {
            dispatchEvent(new LightEvent(LightEvent.COMMAND_SUCCESS, command));
        }

        @Override
        public void error(Peripheral peripheral, Command command,
                          String reason) {
            if (command.tag.equals(TAG_RESET_MESH_ADDRESS)) {
                dispatchEvent(new LightEvent(LightEvent.RESET_MESH_FAILURE, "set address fail"));
            } else {
                dispatchEvent(new LightEvent(LightEvent.COMMAND_FAILURE, command));
            }
        }

        @Override
        public boolean timeout(Peripheral peripheral, Command command) {
            return false;
        }
    }

    private final class OtaRunnable implements Runnable {

        @Override
        public void run() {
            if (!validateOta())
                sendNextOtaPacketCommand();
        }
    }

    private final class OtaPacketParser {

        private int total;
        private int index = -1;
        private byte[] data;
        private int progress;

        public void set(byte[] data) {
            this.clear();

            this.data = data;
            int length = this.data.length;
            int size = 16;

            if (length % size == 0) {
                total = length / size;
            } else {
                total = (int) Math.floor(length / size + 1);
            }
        }

        public void clear() {
            this.progress = 0;
            this.total = 0;
            this.index = -1;
            this.data = null;
        }

        public boolean hasNextPacket() {
            return this.total > 0 && (this.index + 1) < this.total;
        }

        public int getNextPacketIndex() {
            return this.index + 1;
        }

        public byte[] getNextPacket() {

            int index = this.getNextPacketIndex();
            byte[] packet = this.getPacket(index);
            this.index = index;

            return packet;
        }

        public byte[] getPacket(int index) {

            int length = this.data.length;
            int size = 16;
            int packetSize;

            if (length > size) {
                if ((index + 1) == this.total) {
                    packetSize = length - index * size;
                } else {
                    packetSize = size;
                }
            } else {
                packetSize = length;
            }

            packetSize = packetSize + 4;
            byte[] packet = new byte[packetSize];

            System.arraycopy(this.data, index * size, packet, 2, packetSize - 4);

            this.fillIndex(packet, index);
            int crc = this.crc16(packet);
            this.fillCrc(packet, crc);
            TelinkLog.d("ota packet ---> index : " + index + " total : " + this.total + " crc : " + crc + " content : " + Arrays.bytesToHexString(packet, ":"));
            return packet;
        }

        public byte[] getCheckPacket() {
            byte[] packet = new byte[4];
            int index = this.getNextPacketIndex();
            this.fillIndex(packet, index);
            int crc = this.crc16(packet);
            this.fillCrc(packet, crc);
            TelinkLog.d("ota check packet ---> index : " + index + " crc : " + crc + " content : " + Arrays.bytesToHexString(packet, ":"));
            return packet;
        }

        public void fillIndex(byte[] packet, int index) {
            int offset = 0;
            packet[offset++] = (byte) (index & 0xFF);
            packet[offset] = (byte) (index >> 8 & 0xFF);
        }

        public void fillCrc(byte[] packet, int crc) {
            int offset = packet.length - 2;
            packet[offset++] = (byte) (crc & 0xFF);
            packet[offset] = (byte) (crc >> 8 & 0xFF);
        }

        public int crc16(byte[] packet) {

            int length = packet.length - 2;
            short[] poly = new short[]{0, (short) 0xA001};
            int crc = 0xFFFF;
            int ds;

            for (int j = 0; j < length; j++) {

                ds = packet[j];

                for (int i = 0; i < 8; i++) {
                    crc = (crc >> 1) ^ poly[(crc ^ ds) & 1] & 0xFFFF;
                    ds = ds >> 1;
                }
            }

            return crc;
        }

        private boolean invalidateProgress() {

            float a = this.getNextPacketIndex();
            float b = this.total;

            int progress = (int) Math.floor((a / b * 100));

            if (progress == this.progress)
                return false;

            this.progress = progress;

            return true;
        }

        public int getProgress() {
            return this.progress;
        }
    }

    private boolean isN() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.N;
    }
}
