/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.telink.bluetooth.Command;
import com.telink.bluetooth.LeBluetooth;
import com.telink.bluetooth.Peripheral;
import com.telink.bluetooth.TelinkLog;
import com.telink.util.Arrays;
import com.telink.util.Event;
import com.telink.util.EventListener;
import com.telink.util.Strings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LightAdapter {

    public static final int STATUS_CONNECTING = 0;
    public static final int STATUS_CONNECTED = 1;
    public static final int STATUS_LOGINING = 2;
    public static final int STATUS_LOGIN = 3;
    public static final int STATUS_LOGOUT = 4;
    public static final int STATUS_ERROR_N = 5; // android N
    public static final int STATUS_UPDATE_MESH_COMPLETED = 10;
    public static final int STATUS_UPDATING_MESH = 11;
    public static final int STATUS_UPDATE_MESH_FAILURE = 12;
    public static final int STATUS_UPDATE_ALL_MESH_COMPLETED = 13;
    public static final int STATUS_GET_LTK_COMPLETED = 20;
    public static final int STATUS_GET_LTK_FAILURE = 21;
    public static final int STATUS_MESH_OFFLINE = 30;
    public static final int STATUS_MESH_SCAN_COMPLETED = 40;
    public static final int STATUS_MESH_SCAN_TIMEOUT = 41;
    public static final int STATUS_OTA_COMPLETED = 50;
    public static final int STATUS_OTA_FAILURE = 51;
    public static final int STATUS_OTA_PROGRESS = 52;
    public static final int STATUS_GET_FIRMWARE_COMPLETED = 60;
    public static final int STATUS_GET_FIRMWARE_FAILURE = 61;
    public static final int STATUS_DELETE_COMPLETED = 70;
    public static final int STATUS_DELETE_FAILURE = 71;

    public static final int MODE_IDLE = 1;
    public static final int MODE_SCAN_MESH = 2;
    public static final int MODE_UPDATE_MESH = 4;
    public static final int MODE_AUTO_CONNECT_MESH = 8;
    public static final int MODE_OTA = 16;

    public static final int AUTO_REFRESH_NOTIFICATION_DELAY = 2 * 1000;
    public static final int CHECK_OFFLINE_TIME = 10 * 1000;
    public static final int MIN_SCAN_PERIOD = 10 * 1000;

    private static final int STATE_PENDING = 1;
    private static final int STATE_RUNNING = 2;

    //自动连接时最大重连次数
    private static final int CONNECT_MAX_RETRY = 2;

    private final EventListener<Integer> mConnectionListener = new ConnectionListener();
    private final EventListener<Integer> mResetMeshListener = new ResetMeshListener();
    private final EventListener<Integer> mOtaListener = new OtaListener();
    private final EventListener<Integer> mFirmwareListener = new GetFirmwareListener();
    private final EventListener<Integer> mGetLtkListener = new GetLongTermKeyListener();
    private final EventListener<Integer> mNotificationListener = new NotificationListener();
    private final EventListener<Integer> mDeleteListener = new DeleteListener();
    private final EventListener<Integer> mCommandListener = new NormalCommandListener();

    private final AtomicInteger mode = new AtomicInteger(MODE_IDLE);
    private final AtomicInteger state = new AtomicInteger(0);
    private final AtomicInteger status = new AtomicInteger(-1);
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    private final AtomicBoolean isScanStopped = new AtomicBoolean(true);

    protected Callback mCallback;
    protected Context mContext;
    protected Parameters mParams;
    protected LightController mLightCtrl;
    private LightPeripherals mScannedLights;
    private LightPeripherals mUpdateLights;
    private Handler mLoopHandler;
    private Runnable mLoopTask;
    private int mInterval = 200;
    private Handler mNotifyHandler;
    private Runnable mNotifyTask;

    private int lightCount = 0;
    private AtomicInteger updateCount = new AtomicInteger(0);
    private AtomicInteger nextLightIndex = new AtomicInteger(0);

    private ScanCallback mScanCallback;

    private boolean mainLoopRunning = false;
    private boolean autoRefreshRunning;
    private Parameters autoRefreshParams;
    private int autoRefreshCount;

    private long lastLogoutTime;
    private long lastScanTime;

    // 蓝牙扫描开启时间
    private long scanStartTime;
    // 扫描延时
    private Handler mScanDelayHandler;

    private HandlerThread mThread;

    /********************************************************************************
     * Public API
     *******************************************************************************/

    public Parameters getParameters() {
        return this.mParams;
    }

    public int getMode() {
        return this.mode.get();
    }

    synchronized private void setMode(int value) {
        this.mode.getAndSet(value);
        if (mLightCtrl != null) {
            mLightCtrl.setIsUpdating(value == MODE_UPDATE_MESH);
        }
        TelinkLog.d("set mode : " + value);
    }

    private String getModeStr(int value) {
        switch (value) {
            case MODE_IDLE:
                return "IDLE";
            case MODE_SCAN_MESH:
                return "SCAN";
            case MODE_UPDATE_MESH:
                return "UPDATE";
            case MODE_AUTO_CONNECT_MESH:
                return "AUTO CONNECT";
            default:
                return "NULL";
        }
    }

    public boolean isLogin() {
        return this.mLightCtrl.isLogin();
    }

    /********************************************************************************
     * Public API
     *******************************************************************************/

    synchronized public void start(Context context) {

        TelinkLog.d("light mAdapter start");

        if (this.isStarted.get())
            return;

        this.setIsStarted(true);
        this.setMode(MODE_IDLE);
        this.mContext = context;

        this.mScannedLights = new LightPeripherals();
        this.mUpdateLights = new LightPeripherals();
        this.mScanCallback = new ScanCallback();

        this.mLightCtrl = new LightController();
        this.mLightCtrl.addEventListener(LightController.LightEvent.NOTIFICATION_RECEIVE, this.mNotificationListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.CONNECT_SUCCESS, this.mConnectionListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.CONNECT_FAILURE, this.mConnectionListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.CONNECT_FAILURE_N, this.mConnectionListener);// android N
        this.mLightCtrl.addEventListener(LightController.LightEvent.LOGIN_SUCCESS, this.mConnectionListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.LOGIN_FAILURE, this.mConnectionListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.RESET_MESH_SUCCESS, this.mResetMeshListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.RESET_MESH_FAILURE, this.mResetMeshListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.OTA_SUCCESS, this.mOtaListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.OTA_PROGRESS, this.mOtaListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.OTA_FAILURE, this.mOtaListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.GET_FIRMWARE_SUCCESS, this.mFirmwareListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.GET_FIRMWARE_FAILURE, this.mFirmwareListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.GET_LTK_SUCCESS, this.mGetLtkListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.GET_LTK_FAILURE, this.mGetLtkListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.DELETE_SUCCESS, this.mDeleteListener);
        this.mLightCtrl.addEventListener(LightController.LightEvent.DELETE_FAILURE, this.mDeleteListener);
        //this.mLightCtrl.addEventListener(LightController.LightEvent.COMMAND_SUCCESS, this.mCommandListener);
        //this.mLightCtrl.addEventListener(LightController.LightEvent.COMMAND_FAILURE, this.mCommandListener);

        this.mThread = new HandlerThread("LightAdapter Thread");
        this.mThread.start();
        this.mLoopHandler = new Handler(this.mThread.getLooper());
        this.mLoopTask = new EventLoopTask();

        this.mNotifyHandler = new Handler(this.mThread.getLooper());
        this.mNotifyTask = new RefreshNotifyTask();
        this.enableLoop(true);
        this.mScanDelayHandler = new Handler();
        LeBluetooth.getInstance().setLeScanCallback(this.mScanCallback);
    }

    synchronized public void stop() {

        TelinkLog.d("light mAdapter stop");

        if (!this.isStarted.get())
            return;

        this.setIsStarted(false);
        this.setMode(MODE_IDLE);
        stopLeScan();
        this.enableLoop(false);
        this.enableRefreshNotify(false);

        if (this.mLoopHandler != null) {
            this.mLoopHandler.removeCallbacksAndMessages(null);
            this.mLoopHandler = null;
        }

        this.mLoopTask = null;
        this.mContext = null;
        this.mThread.quit();
        this.mThread = null;

        if (this.mNotifyHandler != null) {
            this.mNotifyHandler.removeCallbacksAndMessages(null);
            this.mNotifyHandler = null;
        }

        this.mNotifyTask = null;

        if (this.mScanDelayHandler != null) {
            this.mScanDelayHandler.removeCallbacksAndMessages(null);
            this.mScanDelayHandler = null;
        }

        this.mLightCtrl.removeEventListeners();
        this.mLightCtrl.disconnect();
        this.mLightCtrl = null;

        this.mScanCallback = null;

        this.mScannedLights.clear();
        this.mScannedLights = null;

        this.mUpdateLights.clear();
        this.mUpdateLights = null;

        this.mParams = null;
    }

    /********************************************************************************
     * Public API
     *******************************************************************************/

    public boolean connect(String mac, int timeoutSeconds) {

        if (!this.isStarted.get())
            return false;

        int index = this.mScannedLights.getPeripheralIndex(mac);

        if (index == -1)
            return false;

        TelinkLog.d("LightAdapter#connect");
        LightPeripheral light = this.mScannedLights.get(mac);
        this.connect(light, timeoutSeconds);
        return true;
    }

    public void disconnect() {

        if (!this.isStarted.get())
            return;
        TelinkLog.d("LightAdapter#disconnect");
        this.mLightCtrl.disconnect();
    }

    private void connect(LightPeripheral light, int timeoutSeconds) {

        LightPeripheral currentLight = this.mLightCtrl.getCurrentLight();

        if (currentLight != null && currentLight.isConnected())
            this.mLightCtrl.disconnect();

        this.mLightCtrl.setTimeoutSeconds(timeoutSeconds);
        this.mLightCtrl.connect(this.mContext, light);
        setStatus(STATUS_CONNECTING);
    }

    public boolean login(byte[] meshName, byte[] password) {

        if (!this.isStarted.get())
            return false;

        LightPeripheral light = this.mLightCtrl.getCurrentLight();
        if (light == null || !light.isConnected())
            return false;
        TelinkLog.d("LightAdapter#login");
        this.mLightCtrl.login(meshName, password);
        return true;
    }

    public boolean startOta(byte[] firmware) {
        if (!this.isStarted.get())
            return false;
        LightPeripheral light = this.mLightCtrl.getCurrentLight();
        if (light == null || !this.mLightCtrl.isLogin())
            return false;
        TelinkLog.d("LightAdapter#startOta");
        this.mLightCtrl.startOta(firmware);
        return true;
    }

    public boolean getFirmwareVersion() {
        if (!this.isStarted.get())
            return false;
        LightPeripheral light = this.mLightCtrl.getCurrentLight();
        if (light == null || !light.isConnected())
            return false;
        TelinkLog.d("LightAdapter#getFirmwareVersion");
        this.mLightCtrl.requestFirmware();
        return true;
    }


    private void login(LightPeripheral light) {

        byte[] meshName = java.util.Arrays.copyOf(light.getMeshName(), 16);
        String pwd = mParams.getString(Parameters.PARAM_MESH_PASSWORD);
        if (pwd == null) {
            return;
        }
        byte[] password = Strings.stringToBytes(pwd, 16);

        this.login(meshName, password);
    }

    public boolean sendCommand(byte opcode, int address, byte[] params) {
        return this.sendCommand(opcode, address, params, null, 0);
    }

    public boolean sendCommand(byte opcode, int address, byte[] params, Object tag) {
        return this.sendCommand(opcode, address, params, tag, 0);
    }

    public boolean sendCommand(byte opcode, int address, byte[] params, int delay) {
        return this.sendCommand(opcode, address, params, null, delay);
    }

    public boolean sendCommand(byte opcode, int address, byte[] params, Object tag, int delay) {

        if (!this.isStarted.get())
            return false;

        if (!this.mLightCtrl.isLogin())
            return false;

        if (tag == null)
            return this.mLightCtrl.sendCommand(opcode, address, params, false, delay);
        else
            return this.mLightCtrl.sendCommand(opcode, address, params, false, tag, delay);
    }

    public boolean sendCommandNoResponse(byte opcode, int address, byte[] params) {
        return this.sendCommandNoResponse(opcode, address, params, null, 0);
    }

    public boolean sendCommandNoResponse(byte opcode, int address, byte[] params, Object tag) {
        return this.sendCommandNoResponse(opcode, address, params, tag, 0);
    }

    public boolean sendCommandNoResponse(byte opcode, int address, byte[] params, int delay) {
        return this.sendCommandNoResponse(opcode, address, params, null, delay);
    }

    public boolean sendCommandNoResponse(byte opcode, int address, byte[] params, Object tag, int delay) {

        if (!this.isStarted.get())
            return false;

        if (!this.mLightCtrl.isLogin())
            return false;

        if (tag == null)
            return this.mLightCtrl.sendCommand(opcode, address, params, true, delay);
        else
            return this.mLightCtrl.sendCommand(opcode, address, params, true, tag, delay);
    }

    synchronized public void startScan(Parameters params, Callback callback) {

        if (!this.isStarted.get())
            return;

        if (this.getMode() == MODE_SCAN_MESH)
            return;
        TelinkLog.d("LightAdapter#startLeScan");
        this.setMode(MODE_IDLE);

        if (!isSupportN())
            LeBluetooth.getInstance().stopScan();

        this.mParams = params;
        this.mCallback = callback;
        this.mUpdateLights.clear();
        this.mScannedLights.clear();
        this.mLightCtrl.disconnect();

        this.lastScanTime = System.currentTimeMillis();
        this.setMode(MODE_SCAN_MESH);
        this.enableLoop(true);
    }

    synchronized public void updateMesh(Parameters params, Callback callback) {

        if (!this.isStarted.get())
            return;

        if (this.getMode() == MODE_UPDATE_MESH)
            return;
        TelinkLog.d("LightAdapter#updateMesh");
        this.setMode(MODE_IDLE);

        if (!isSupportN())
            LeBluetooth.getInstance().stopScan();
//        LeBluetooth.getInstance().stopScan();

        this.mParams = params;
        this.mCallback = callback;

        Object updateObj = this.mParams.get(Parameters.PARAM_DEVICE_LIST);
        this.mUpdateLights.clear();

        if (updateObj != null) {

            if (updateObj instanceof DeviceInfo) {
                DeviceInfo deviceInfo = (DeviceInfo) updateObj;
                LightPeripheral peripheral = this.mScannedLights.get(deviceInfo.macAddress);
                if (peripheral != null) {
                    peripheral.setNewMeshAddress(deviceInfo.meshAddress);
                    this.mUpdateLights.put(peripheral);
                }
            } else if (updateObj instanceof Iterable) {
                @SuppressWarnings("unchecked")
                Iterable<DeviceInfo> iterable = (Iterable<DeviceInfo>) updateObj;
                Iterator<DeviceInfo> iterator = iterable.iterator();

                DeviceInfo deviceInfo;

                while (iterator.hasNext()) {

                    deviceInfo = iterator.next();

                    if (deviceInfo != null) {
                        LightPeripheral peripheral = this.mScannedLights.get(deviceInfo.macAddress);

                        if (peripheral != null) {
                            peripheral.setNewMeshAddress(deviceInfo.meshAddress);
                            this.mUpdateLights.put(peripheral);
                        }
                    }
                }
            }

        } else {
            this.mScannedLights.copyTo(this.mUpdateLights);
        }

        this.nextLightIndex.set(0);
        this.updateCount.set(0);
        this.lightCount = this.mUpdateLights.size();
        this.mLightCtrl.disconnect();

        this.setMode(MODE_UPDATE_MESH);
        this.setState(STATE_RUNNING);

        this.enableLoop(true);
    }

    synchronized public void autoConnect(Parameters params, Callback callback) {

        if (!this.isStarted.get())
            return;

        if (this.getMode() == MODE_AUTO_CONNECT_MESH)
            return;
        TelinkLog.d("LightAdapter#autoConnect");
        this.setMode(MODE_IDLE);

        if (!isSupportN())
            LeBluetooth.getInstance().stopScan();
//        LeBluetooth.getInstance().stopScan();

        this.mParams = params;
        this.mCallback = callback;
        this.mScannedLights.clear();
        this.mUpdateLights.clear();
        this.mLightCtrl.disconnect();
        this.lightCount = 0;
        this.updateCount.set(0);
        this.nextLightIndex.set(0);

        this.lastLogoutTime = 0;

        this.setMode(MODE_AUTO_CONNECT_MESH);
        this.setState(STATE_RUNNING);

        this.enableLoop(true);
    }


    private void stopLeScan() {
        long delay;
        if (isSupportN() && System.currentTimeMillis() - scanStartTime < MIN_SCAN_PERIOD) {
            delay = MIN_SCAN_PERIOD - (System.currentTimeMillis() - scanStartTime);
        } else {
            delay = 0;
        }
        mScanDelayHandler.postDelayed(stopScanTask, delay);
    }

    private Runnable stopScanTask = new Runnable() {
        @Override
        public void run() {
            if (!isScanStopped.get()) {
                LeBluetooth.getInstance().stopScan();
            }
        }
    };

    synchronized public void idleMode(boolean disconnect) {

        if (!this.isStarted.get())
            return;
        if (this.getMode() == MODE_IDLE)
            return;
//        TelinkLog.d("LightAdapter#idleMode");
        this.setMode(MODE_IDLE);
        this.status.getAndSet(-1);
        this.enableLoop(false);

        if (disconnect) {
            this.mLightCtrl.disconnect();
        }

        stopLeScan();
//        LeBluetooth.getInstance().stopScan();
    }

    synchronized public void startOta(Parameters params, Callback callback) {

        if (!this.isStarted.get())
            return;

        if (this.getMode() == MODE_OTA)
            return;
        TelinkLog.d("LightAdapter#startOta");
        this.setMode(MODE_IDLE);

//        LeBluetooth.getInstance().stopScan();

        this.mParams = params;
        this.mCallback = callback;
        this.mUpdateLights.clear();
        this.lightCount = 0;
        this.updateCount.set(0);
        this.nextLightIndex.set(0);

        this.setMode(MODE_OTA);
        this.setState(STATE_RUNNING);
        this.enableLoop(true);
    }


    public void enableAutoRefreshNotify(Parameters params) {

        if (!this.isStarted.get())
            return;
        TelinkLog.d("LightAdapter#enableAutoRefreshNotify");
        this.autoRefreshParams = params;
        this.autoRefreshCount = 0;
        this.enableRefreshNotify(true);
    }

    public void disableAutoRefreshNotify() {

        if (!this.isStarted.get())
            return;
        TelinkLog.d("LightAdapter#disableAutoRefreshNotify");
        this.enableRefreshNotify(false);
        this.autoRefreshParams = null;
    }

    public void enableNotification() {
        if (!this.isStarted.get())
            return;
        TelinkLog.d("LightAdapter#enableNotification");
        this.mLightCtrl.enableNotification();
    }

    public void disableNotification() {
        if (!this.isStarted.get())
            return;
        TelinkLog.d("LightAdapter#disableNotification");
        this.mLightCtrl.disableNotification();
    }

    public void updateNotification() {
        if (!this.isStarted.get())
            return;
        TelinkLog.d("LightAdapter#updateNotification");
        this.mLightCtrl.updateNotification();
    }

    public void updateNotification(byte[] params) {
        if (!this.isStarted.get())
            return;
        TelinkLog.d("LightAdapter#updateNotification-with-params");
        this.mLightCtrl.updateNotification(params);
    }

    /********************************************************************************
     * Protected API
     *******************************************************************************/

    protected LightPeripheral onLeScan(BluetoothDevice device, int rssi,
                                       byte[] scanRecord) {
        AdvertiseFilterChain filterChain = AdvertiseFilterChain.getDefault();
        Iterator<AdvertiseDataFilter> iterator = filterChain.iterator();
        AdvertiseDataFilter filter;
        LightPeripheral light = null;

        while (iterator.hasNext()) {
            filter = iterator.next();
            try {
                light = filter.filter(device, rssi, scanRecord);
            } catch (Exception e) {
                TelinkLog.d("Advertise Filter Exception : " + filter.toString() + "--" + e.getMessage());
            }

            if (light != null)
                break;
        }

        return light;
    }

    protected boolean onLeScanFilter(LightPeripheral light) {

        int mode = this.getMode();

        Parameters params = this.getParameters();

        if (params == null)
            return false;

        byte[] outOfMeshName;

        byte[] meshName = Strings.stringToBytes(params.getString(Parameters.PARAM_MESH_NAME), 16);
        byte[] meshName1 = light.getMeshName();

        if (mode == MODE_SCAN_MESH) {
            outOfMeshName = Strings.stringToBytes(params.getString(Parameters.PARAM_OUT_OF_MESH), 16);
            if (!Arrays.equals(meshName, meshName1) && !Arrays.equals(outOfMeshName, meshName1))
                return false;
        } else if (mode == MODE_AUTO_CONNECT_MESH) {
            if (!Arrays.equals(meshName, meshName1))
                return false;
            String mac = params.getString(Parameters.PARAM_AUTO_CONNECT_MAC);
            return TextUtils.isEmpty(mac) || mac.equals(light.getMacAddress());
        }

        return true;
    }

    protected void onNotification(byte[] data) {

        int length = data.length;
        int minLength = 20;
        int position = 7;

        if (length < minLength)
            return;

        int opcode = data[position++] & 0xFF;
        int vendorId = (data[position++] << 8) + data[position];

        if (vendorId != Manufacture.getDefault().getVendorId())
            return;

        int src = data[3] + (data[4] << 8);
        byte[] params = new byte[10];

        System.arraycopy(data, 10, params, 0, 10);

        if (this.mCallback != null)
            this.mCallback.onNotify(this.mLightCtrl.getCurrentLight(), getMode(),
                    opcode, src, params);
    }

    /********************************************************************************
     * Private API
     *******************************************************************************/

    private int getState() {
        return this.state.get();
    }

    synchronized private void setState(int value) {
        this.state.getAndSet(value);
    }

    synchronized private void setIsStarted(boolean value) {
        this.isStarted.getAndSet(value);
    }

    synchronized private void setStatus(int newStatus, boolean ignoreIdleMode, boolean ignoreStatus) {

        if (!ignoreIdleMode) {
            if (this.getMode() == MODE_IDLE)
                return;
        }

        if (!ignoreStatus) {
            if (this.status.get() == newStatus)
                return;
        }

        int oldStatus = this.status.getAndSet(newStatus);

        if (mCallback != null)
            mCallback.onStatusChanged(this.mLightCtrl,
                    this.getMode(), oldStatus, newStatus);
    }

    private void setStatus(int newStatus, boolean ignoreIdleMode) {
        this.setStatus(newStatus, ignoreIdleMode, false);
    }

    private void setStatus(int newStatus) {
        this.setStatus(newStatus, false, false);
    }

    private void enableLoop(boolean running) {

        if (this.mLoopHandler == null || this.mLoopTask == null)
            return;

        if (running) {

            if (!this.mainLoopRunning) {
                this.mLoopHandler.postDelayed(this.mLoopTask,
                        this.mInterval);
//                this.mLoopHandler.post(this.mLoopTask);
            }

        } else {
            this.mLoopHandler.removeCallbacks(this.mLoopTask);
        }

        this.mainLoopRunning = running;
    }

    private void enableRefreshNotify(boolean enable) {

        if (this.mNotifyHandler == null || this.mNotifyTask == null)
            return;

        if (enable) {

            if (this.autoRefreshRunning)
                return;

            this.autoRefreshCount = 0;
            this.autoRefreshRunning = true;
            /*int delay = mParams.getInt(
                    Parameters.PARAM_AUTO_REFRESH_NOTIFICATION_DELAY,
                    AUTO_REFRESH_NOTIFICATION_DELAY);*/
            this.mNotifyHandler.postDelayed(this.mNotifyTask, 0);
        } else {
            this.mNotifyHandler.removeCallbacks(this.mNotifyTask);
            this.autoRefreshRunning = false;
        }
    }

    public interface Callback {

        boolean onLeScan(LightPeripheral light, int mode, byte[] scanRecord);

        void onStatusChanged(LightController controller, int mode, int oldStatus,
                             int newStatus);

        void onNotify(LightPeripheral light, int mode, int opcode, int src,
                      byte[] params);

        void onCommandResponse(LightPeripheral light, int mode, Command command, boolean success);

        void onError(int errorCode);
    }

    private final class ScanCallback implements LeBluetooth.LeScanCallback {

        public ScanCallback() {
        }

        @Override
        public void onScanFail(int errorCode) {
            TelinkLog.d(" scan fail : " + errorCode);
            if (mCallback != null)
                mCallback.onError(errorCode);
        }

        @Override
        public void onStartedScan() {
            scanStartTime = System.currentTimeMillis();
            isScanStopped.set(false);
        }

        @Override
        public void onStoppedScan() {
            isScanStopped.set(true);
        }

        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

//            TelinkLog.d("Scan : " + device.getName() + "-" + device.getAddress());

            if (mCallback == null || getMode() == MODE_IDLE || getMode() == MODE_UPDATE_MESH)
                return;

            synchronized (LightAdapter.this) {
                if (mScannedLights.contains(device.getAddress()))
                    return;
            }

            LightPeripheral light = LightAdapter.this.onLeScan(device,
                    rssi, scanRecord);

            if (light == null)
                return;

            // Device Name 有可能与 广播包中的MeshName不一致
//            saveLog("Scan MeshInfo : " + light.getMeshNameStr() + "-" + light.getMacAddress());
            boolean result = onLeScanFilter(light);

            if (!result)
                return;

            TelinkLog.d("add scan result : " + device.getAddress());
//            saveLog("add scan result : " + device.getAddress());

            int mode = getMode();

            if (mode == MODE_SCAN_MESH) {

                boolean isSingleScan = mParams.getBoolean(Parameters.PARAM_SCAN_TYPE_SINGLE, false);

                if (isSingleScan) {

                    if (mScannedLights.size() == 0) {
                        mScannedLights.put(light);
                        mCallback.onLeScan(light, mode, scanRecord);
                    }

                } else {
                    mScannedLights.put(light);
                    mCallback.onLeScan(light, mode, scanRecord);
                }

            } else if (mode == MODE_AUTO_CONNECT_MESH) {
                mScannedLights.put(light);
            } else if (mode == MODE_OTA) {
                mScannedLights.put(light);
            }
        }

    }

    private int conFail = 0;

    private final class ConnectionListener implements EventListener<Integer> {

        private void onConnected() {

            setStatus(STATUS_CONNECTED, true);

            int mode = getMode();

            if (mode != MODE_IDLE) {

                if (mode == MODE_UPDATE_MESH) {
                    mLightCtrl.requestFirmware();
                } /*else if (mode == MODE_AUTO_CONNECT_MESH) {
                    mScannedLights.removeTop();
                }*/

                setStatus(STATUS_LOGINING);

                LightPeripheral light = mLightCtrl.getCurrentLight();
                login(light);
            } else {
                mLightCtrl.requestFirmware();
            }
        }

        private void onLoginSuccess() {

            TelinkLog.d("onLoginSuccess "
                    + mLightCtrl.getCurrentLight().getMacAddress());

            setStatus(STATUS_LOGIN, true);

            int mode = getMode();

            if (mode == MODE_UPDATE_MESH) {

                setStatus(STATUS_UPDATING_MESH);

                byte[] meshName = Strings.stringToBytes(mParams.getString(Parameters.PARAM_NEW_MESH_NAME), 16);
                byte[] password = Strings.stringToBytes(mParams.getString(Parameters.PARAM_NEW_PASSWORD), 16);
                byte[] ltk = mParams.getBytes(Parameters.PARAM_LONG_TERM_KEY);

                mLightCtrl.reset(meshName, password, ltk);

            } else if (mode == MODE_AUTO_CONNECT_MESH) {
                enableLoop(false);
                setState(STATE_PENDING);
                stopLeScan();
                mScannedLights.clear();
                nextLightIndex.set(0);
                lastLogoutTime = 0;
                boolean enable = mParams.getBoolean(Parameters.PARAM_AUTO_ENABLE_NOTIFICATION);
                if (enable) {
                    mLightCtrl.enableNotification();
                    if (autoRefreshParams != null) {
                        autoRefreshRunning = false;
                        enableRefreshNotify(true);
                    } else {
                        mLightCtrl.updateNotification();
                    }
                }

            } else if (mode == MODE_OTA) {
                setState(STATE_PENDING);
                OtaDeviceInfo otaDeviceInfo = (OtaDeviceInfo) mParams.get(Parameters.PARAM_DEVICE_LIST);
                mLightCtrl.startOta(otaDeviceInfo.firmware);
            }
        }

        private void onLoginFailure() {

            TelinkLog.d("onLoginFail "
                    + mLightCtrl.getCurrentLight().getMacAddress());

            setStatus(STATUS_LOGOUT, true);

            int mode = getMode();

            if (mode == MODE_UPDATE_MESH) {
                setState(STATE_RUNNING);
                setStatus(STATUS_UPDATE_MESH_FAILURE);
            } else if (mode == MODE_AUTO_CONNECT_MESH) {
//                mScannedLights.clear();
                mScannedLights.removeTop();
                nextLightIndex.set(0);
//                lastLogoutTime = 0;
                setState(STATE_RUNNING);
                enableLoop(true);
            } else if (mode == MODE_OTA) {
                setState(STATE_PENDING);
                setStatus(STATUS_OTA_FAILURE);
            }
        }

        private void onNError() {
            TelinkLog.d("onNError "
                    + mLightCtrl.getCurrentLight().getMacAddress());

            setStatus(STATUS_LOGOUT, true);

            int mode = getMode();
            if (mode == MODE_UPDATE_MESH) {
                setState(STATE_RUNNING);
                setStatus(STATUS_ERROR_N);
            }
        }

        @Override
        public void performed(Event<Integer> event) {
            switch (event.getType()) {
                case LightController.LightEvent.CONNECT_SUCCESS:
                    this.onConnected();
                    break;
                case LightController.LightEvent.LOGIN_SUCCESS:
                    this.onLoginSuccess();
                    break;
                case LightController.LightEvent.LOGIN_FAILURE:
                case LightController.LightEvent.CONNECT_FAILURE:
                    this.onLoginFailure();
                    break;

                case LightController.LightEvent.CONNECT_FAILURE_N:
                    onNError();
                    break;
            }
        }
    }

    private final class NormalCommandListener implements EventListener<Integer> {

        private void onCommandSuccess(Command command) {

            if (mCallback != null)
                mCallback.onCommandResponse(mLightCtrl.getCurrentLight(), getMode(), command, true);
        }

        private void onCommandFailure(Command command) {

            if (mCallback != null)
                mCallback.onCommandResponse(mLightCtrl.getCurrentLight(), getMode(), command, false);
        }

        @Override
        public void performed(Event<Integer> event) {
            LightController.LightEvent lightEvent = (LightController.LightEvent) event;
            switch (lightEvent.getType()) {
                case LightController.LightEvent.COMMAND_SUCCESS:
                    this.onCommandSuccess((Command) lightEvent.getArgs());
                    break;
                case LightController.LightEvent.COMMAND_FAILURE:
                    this.onCommandFailure((Command) lightEvent.getArgs());
                    break;
            }
        }
    }

    private final class NotificationListener implements EventListener<Integer> {

        @Override
        public void performed(Event<Integer> event) {
            if (mCallback != null) {
                LightController.LightEvent lightEvent = (LightController.LightEvent) event;
                LightAdapter.this.onNotification((byte[]) lightEvent.getArgs());
            }
        }
    }

    private final class ResetMeshListener implements EventListener<Integer> {

        private void onResetMeshSuccess() {

            TelinkLog.d("onResetMeshSuccess "
                    + mLightCtrl.getCurrentLight().getMacAddress());

            setStatus(STATUS_UPDATE_MESH_COMPLETED);

            if (getMode() == MODE_UPDATE_MESH) {
                updateCount.getAndIncrement();
                setState(STATE_RUNNING);
            }
        }

        private void onResetMeshFailure(String reason) {

            TelinkLog.d("onResetMeshFail "
                    + mLightCtrl.getCurrentLight().getMacAddress()
                    + " error msg : " + reason);

            setStatus(STATUS_UPDATE_MESH_FAILURE);

            if (getMode() == MODE_UPDATE_MESH) {
                setState(STATE_RUNNING);
            }
        }

        @Override
        public void performed(Event<Integer> event) {
            LightController.LightEvent lightEvent = (LightController.LightEvent) event;
            switch (lightEvent.getType()) {
                case LightController.LightEvent.RESET_MESH_SUCCESS:
                    this.onResetMeshSuccess();

                    break;
                case LightController.LightEvent.RESET_MESH_FAILURE:
                    this.onResetMeshFailure((String) lightEvent.getArgs());
                    break;
            }
        }
    }

    private final class GetFirmwareListener implements EventListener<Integer> {

        private void onGetFirmwareSuccess() {
            int mode = getMode();
            if (mode == MODE_UPDATE_MESH || mode == MODE_AUTO_CONNECT_MESH || mode == MODE_OTA)
                return;
            setStatus(STATUS_GET_FIRMWARE_COMPLETED, true);
        }

        private void onGetFirmwareFailure() {
            int mode = getMode();
            if (mode == MODE_UPDATE_MESH || mode == MODE_AUTO_CONNECT_MESH || mode == MODE_OTA)
                return;
            setStatus(STATUS_GET_FIRMWARE_FAILURE, true);
        }

        @Override
        public void performed(Event<Integer> event) {
            switch (event.getType()) {
                case LightController.LightEvent.GET_FIRMWARE_SUCCESS:
                    this.onGetFirmwareSuccess();
                    break;
                case LightController.LightEvent.GET_FIRMWARE_FAILURE:
                    this.onGetFirmwareFailure();
                    break;
            }
        }
    }

    private final class GetLongTermKeyListener implements EventListener<Integer> {

        @Override
        public void performed(Event<Integer> event) {
            switch (event.getType()) {
                case LightController.LightEvent.GET_LTK_SUCCESS:
                    setStatus(STATUS_GET_LTK_COMPLETED);
                    break;
                case LightController.LightEvent.GET_LTK_FAILURE:
                    setStatus(STATUS_GET_LTK_FAILURE);
                    break;
            }
        }
    }

    private final class DeleteListener implements EventListener<Integer> {

        @Override
        public void performed(Event<Integer> event) {
            switch (event.getType()) {
                case LightController.LightEvent.DELETE_SUCCESS:
                    setStatus(STATUS_DELETE_COMPLETED);
                    setMode(MODE_IDLE);
                    break;
                case LightController.LightEvent.DELETE_FAILURE:
                    setStatus(STATUS_DELETE_FAILURE);
                    setMode(MODE_IDLE);
                    break;
            }
        }
    }

    private final class OtaListener implements EventListener<Integer> {

        private void onOtaSuccess() {
            TelinkLog.d("OTA Success");
            setStatus(STATUS_OTA_COMPLETED, true);
            setMode(MODE_IDLE);
        }

        private void onOtaFailure() {
            TelinkLog.d("OTA Failure");
            setStatus(STATUS_OTA_FAILURE, true);
            setMode(MODE_IDLE);
        }

        @Override
        public void performed(Event<Integer> event) {
            switch (event.getType()) {
                case LightController.LightEvent.OTA_PROGRESS:
                    setStatus(STATUS_OTA_PROGRESS, true, true);
                    break;
                case LightController.LightEvent.OTA_SUCCESS:
                    this.onOtaSuccess();
                    break;
                case LightController.LightEvent.OTA_FAILURE:
                    this.onOtaFailure();
                    break;
            }
        }
    }

    //    12143;
//    12133
    private final class EventLoopTask implements Runnable {

        private boolean pause;
        private long lastUpdateTime;
        private int waitSeconds = 5 * 1000;

        @Override
        public void run() {

            int mode = getMode();

            if (mode == MODE_SCAN_MESH) {
                this.leScan();
            } else if (mode == MODE_UPDATE_MESH) {
                this.update();
            } else if (mode == MODE_AUTO_CONNECT_MESH) {
                this.autoConnect();
            } else if (mode == MODE_OTA) {
                this.autoOta();
            }

            if (mLoopHandler != null)
                mLoopHandler.postDelayed(this, mInterval);
        }

        private void leScan() {

            if (!this.startLeScan()) {
                setMode(MODE_IDLE);
                return;
            }

            boolean isSingleScan = mParams.getBoolean(Parameters.PARAM_SCAN_TYPE_SINGLE, false);

            if (isSingleScan) {

                if (mScannedLights.size() == 1) {
                    setStatus(STATUS_MESH_SCAN_COMPLETED);
                    idleMode(false);
                    return;
                }
            }

            int timeoutSeconds = mParams.getInt(Parameters.PARAM_SCAN_TIMEOUT_SECONDS, 0);

            if (timeoutSeconds <= 0)
                return;

            long currentTime = System.currentTimeMillis();
            timeoutSeconds = timeoutSeconds * 1000;

            if ((currentTime - lastScanTime) >= timeoutSeconds) {

                TelinkLog.d("scan timeout");

                if (isSingleScan) {
                    setStatus(STATUS_MESH_SCAN_TIMEOUT);
                }

                setStatus(STATUS_MESH_SCAN_COMPLETED);
                idleMode(false);
            }
        }

        private void update() {

            if (getState() == STATE_PENDING)
                return;

            if (updateCount.get() >= lightCount || nextLightIndex.get() >= lightCount) {
                setState(STATE_PENDING);
                nextLightIndex.set(0);
                setStatus(STATUS_UPDATE_ALL_MESH_COMPLETED);
                idleMode(false); //false
                return;
            }

            setState(STATE_PENDING);

            LightPeripheral light = mUpdateLights.get(nextLightIndex.getAndIncrement());

            if (light == null || light.meshChanged) {
                setState(STATE_RUNNING);
                return;
            }

            int timeoutSeconds = mParams
                    .getInt(Parameters.PARAM_TIMEOUT_SECONDS);
            connect(light, timeoutSeconds);
        }

        private void autoConnect() {

            if (getState() == STATE_PENDING)
                return;

            if (pause) {
                long currentTime = System.currentTimeMillis();
                long delay = currentTime - lastUpdateTime;

                if (delay < waitSeconds)
                    return;
                else
                    pause = false;
            }

            if (!this.startLeScan()) {
                setMode(MODE_IDLE);
                return;
            }

            if (this.checkOffLine()) {
                return;
            }

            int count = mScannedLights.size();

            if (count <= 0) {
//                this.checkOffLine();
                return;
            }


           /* if (nextLightIndex >= count) {
                LeBluetooth.getInstance().stopScan();
                mScannedLights.onStop();
                lastLogoutTime = 0;
                nextLightIndex = 0;
                return;
            }*/

            setState(STATE_PENDING);
//            LeBluetooth.getInstance().stopScan();
//            lastLogoutTime = 0;
            int timeoutSeconds = mParams
                    .getInt(Parameters.PARAM_TIMEOUT_SECONDS);
            LightPeripheral light = mScannedLights.getTop();
            if (light != null) {
                connect(light, timeoutSeconds);
            } else {
                setState(STATE_RUNNING);
            }
        }

        private void autoOta() {

            if (getState() == STATE_PENDING)
                return;

            int count = mScannedLights.size();
            if (count <= 0)
                return;

            setState(STATE_PENDING);
            int timeoutSeconds = mParams.getInt(Parameters.PARAM_TIMEOUT_SECONDS);
            OtaDeviceInfo deviceInfo = (OtaDeviceInfo) mParams.get(Parameters.PARAM_DEVICE_LIST);
            LightPeripheral light = mScannedLights.get(deviceInfo.macAddress);

            if (light == null) {
                setStatus(STATUS_OTA_FAILURE);
                setMode(MODE_IDLE);
                return;
            }

            if (light.isConnected()) {
                TelinkLog.d("login");
                login(light);
            } else {
                TelinkLog.d("connect");
                connect(light, timeoutSeconds);
            }
        }

        private boolean startLeScan() {
            mScanDelayHandler.removeCallbacks(stopScanTask);
            if (!LeBluetooth.getInstance().isScanning()) {
                if (!LeBluetooth.getInstance().startScan(null))
                    return false;
                lastLogoutTime = 0;
            }

            return true;
        }

        private boolean checkOffLine() {

            if (lastLogoutTime == 0) {
                lastLogoutTime = System.currentTimeMillis() - mInterval;
                return false;
            }

            int checkOffLineTime = mParams.getInt(Parameters.PARAM_OFFLINE_TIMEOUT_SECONDS, 0) * 1000;

            if (checkOffLineTime <= 0)
                checkOffLineTime = CHECK_OFFLINE_TIME;

            long currentTime = System.currentTimeMillis();

            if ((currentTime - lastLogoutTime) > checkOffLineTime) {
                lastLogoutTime = 0;
                stopScan();
                setStatus(STATUS_MESH_OFFLINE);
                return true;
            } else {
                return false;
            }
        }


        private void stopScan() {
            stopLeScan();
            this.pause = true;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    private final class RefreshNotifyTask implements Runnable {

        @Override
        public void run() {

            if (getMode() != MODE_AUTO_CONNECT_MESH)
                return;

            if (autoRefreshParams == null)
                return;
            if (!mLightCtrl.isLogin())
                return;

            int delay = autoRefreshParams.getInt(
                    Parameters.PARAM_AUTO_REFRESH_NOTIFICATION_DELAY,
                    AUTO_REFRESH_NOTIFICATION_DELAY);

            if (delay <= 0)
                delay = AUTO_REFRESH_NOTIFICATION_DELAY;

            mLightCtrl.updateNotification();
            int repeat = autoRefreshParams.getInt(Parameters.PARAM_AUTO_REFRESH_NOTIFICATION_REPEAT, 1);

            if (repeat > 0) {
                int count = autoRefreshCount + 1;

                if (count > repeat) {
                    autoRefreshRunning = false;
                } else {
                    autoRefreshCount = count;
                    TelinkLog.d("AutoRefresh : " + count);
                    mNotifyHandler.postDelayed(this, delay);
                }
            } else if (repeat <= 0) {
                mNotifyHandler.postDelayed(this, delay);
            }
        }
    }


    private final class LightPeripherals {

        private List<LightPeripheral> mPeripherals;

        public LightPeripherals() {
            this.mPeripherals = new ArrayList<>();
        }

        public void put(LightPeripheral light) {

            int index = this.getPeripheralIndex(light.getMacAddress());

            if (index == -1)
                this.mPeripherals.add(light);
        }

        @Nullable
        public LightPeripheral get(int index) {

            if (index >= 0 && index < this.mPeripherals.size())
                return this.mPeripherals.get(index);

            return null;
        }

        public LightPeripheral getTop() {
            if (this.mPeripherals.size() <= 0) {
                return null;
            }
            return this.mPeripherals.get(0);
        }

        public boolean removeTop() {
            if (this.mPeripherals.size() <= 0) {
                return false;
            }
            if (this.mPeripherals.get(0).getRetry() == 0) {
                this.mPeripherals.get(0).addRetry();
            } else {
                this.mPeripherals.remove(0);
            }

            return true;
        }

        @Nullable
        public LightPeripheral get(String macAddress) {

            int index = this.getPeripheralIndex(macAddress);

            if (index != -1) {
                return this.get(index);
            }

            return null;
        }

        public boolean contains(String macAddress) {

            int index = this.getPeripheralIndex(macAddress);

            return index != -1;
        }

        public int size() {
            synchronized (this) {
                return this.mPeripherals.size();
            }
        }

        public void clear() {
            synchronized (this) {
                this.mPeripherals.clear();
            }
        }

        private int getPeripheralIndex(String macAddress) {

            int count = this.size();

            Peripheral peripheral;

            for (int i = 0; i < count; i++) {

                peripheral = this.mPeripherals.get(i);

                if (peripheral.getMacAddress().equals(macAddress))
                    return i;
            }
            return -1;
        }

        public void copyTo(LightPeripherals dest) {
            for (LightPeripheral light : this.mPeripherals) {
                dest.put(light);
            }
        }

        public LightPeripheral getByMaxRssi() {
            LightPeripheral result = null;

            synchronized (this) {
                for (LightPeripheral light : this.mPeripherals) {
                    if (result == null || light.getRssi() > result.getRssi())
                        result = light;
                }
            }

            return result;
        }
    }

    private boolean isSupportN() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    private boolean isN() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.N;
    }
}
