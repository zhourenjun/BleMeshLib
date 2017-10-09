package com.telink.light

import android.bluetooth.BluetoothGattService
import android.content.Context
import android.os.Build
import android.os.Handler
import com.telink.event.Event
import com.telink.event.EventBus
import com.telink.param.Opcode
import com.telink.param.UuidInformation
import com.telink.util.AES
import com.telink.util.Arrays
import com.telink.util.BleLog
import java.io.UnsupportedEncodingException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.SecureRandom
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import kotlin.experimental.xor


class LightController : EventBus<Int>(), LightPeripheral.Callback {

    private val loginRandm = ByteArray(8)

    private val mDelayHandler = Handler()
    private val mConnectTask = ConnectionRunnable()
    private val nCheckRunnable = NCheckRunnable()
    private val mAllocAddressTask = SetDeviceAddressRunnable()
    private val otaTask = OtaRunnable()

    private val loginCallback = LoginCommandCallback()
    private val resetCallback = ResetCommandCallback()
    private val notifyCallback = NotifyCommandCallback()
    private val normalCallback = NormalCommandCallback()
    private val deleteCallback = DeleteCommandCallback()
    private val otaCallback = OtaCommandCallback()
    private val firmwareCallback = FirmwareCallback()
    private val otaPacketParser = OtaPacketParser()


    lateinit var currentLight: LightPeripheral
    private lateinit var sessionKey: ByteArray
    private var sequenceNumber = Integer.MAX_VALUE
    private val random = SecureRandom()
    var isLogin = false
    private lateinit var meshName: ByteArray
    private lateinit var password: ByteArray
    private lateinit var longTermKey: ByteArray
    private lateinit var newLongTermKey: ByteArray
    private lateinit var newMeshName: ByteArray
    private lateinit var newPassword: ByteArray
    private lateinit var mContext: Context
    private var timeoutSeconds = 15

    // android N check
    private var isChecking = false
    private var failCount = 0
    private var mIsUpdatingMesh = false

    val otaProgress = otaPacketParser.progress

    private val isN = Build.VERSION.SDK_INT == Build.VERSION_CODES.N


    override fun dispatchEvent(event: Event<Int>) {
        super.dispatchEvent(event.setThreadMode(Event.ThreadMode.Background))
    }


    fun setTimeoutSeconds(timeoutSeconds: Int) {
        if (timeoutSeconds > 0) {
            this.timeoutSeconds = timeoutSeconds
        }
    }


    fun setIsUpdating(isUpdating: Boolean) {
        mIsUpdatingMesh = isUpdating
    }


    @Synchronized
    fun connect(context: Context, light: LightPeripheral) {
        failCount = 0
        sequenceNumber = Integer.MAX_VALUE
        mContext = context
        currentLight = light

        currentLight.disconnect()
        currentLight.connect(context, this)

        mDelayHandler.removeCallbacks(mConnectTask)
        mDelayHandler.removeCallbacksAndMessages(null)

        if (timeoutSeconds > 0) {
            mDelayHandler.postDelayed(mConnectTask, (timeoutSeconds * 1000).toLong())
        }
    }

    @Synchronized
    private fun retryConnect() {
        BleLog.d("LightController.retryConnect：" + failCount)
        currentLight.connect(mContext, this)
        mDelayHandler.removeCallbacks(mConnectTask)
        mDelayHandler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    fun disconnect() {
        synchronized(this) {
            isLogin = false
        }

        mDelayHandler.removeCallbacks(mConnectTask)
        mDelayHandler.removeCallbacksAndMessages(null)
        resetOta()

        if (currentLight != null) {
            BleLog.d("LightController.disconnect:" + currentLight!!.deviceName + "--" + currentLight!!.macAddress)
            currentLight.disconnect()
        }

        sequenceNumber = 0

    }

    fun login(meshName: ByteArray, password: ByteArray) {

        this.meshName = meshName
        this.password = password

        if (!AES.Security) {
            synchronized(this) {
                isLogin = true
            }

            dispatchEvent(LightEvent(LightEvent.LOGIN_SUCCESS))
            return
        }

        val plaintext = ByteArray(16)

        for (i in 0..15) {
            plaintext[i] = meshName[i] xor password[i]
        }

        val randm = generateRandom(loginRandm)
        val sk = ByteArray(16)

        System.arraycopy(randm, 0, sk, 0, randm.size)

        val encrypted: ByteArray?

        try {
            encrypted = AES.encrypt(sk, plaintext)
        } catch (e: InvalidKeyException) {
            disconnect()
            dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, e.message!!))
            return
        } catch (e: NoSuchAlgorithmException) {
            disconnect()
            dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, e.message!!))
            return
        } catch (e: NoSuchPaddingException) {
            disconnect()
            dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, e.message!!))
            return
        } catch (e: UnsupportedEncodingException) {
            disconnect()
            dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, e.message!!))
            return
        } catch (e: IllegalBlockSizeException) {
            disconnect()
            dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, e.message!!))
            return
        } catch (e: BadPaddingException) {
            disconnect()
            dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, e.message!!))
            return
        } catch (e: NoSuchProviderException) {
            disconnect()
            dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, e.message!!))
            return
        }

        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.PAIR)

        val commandData = ByteArray(17)

        commandData[0] = Opcode.BLE_GATT_OP_PAIR_ENC_REQ.getValue()

        System.arraycopy(randm, 0, commandData, 1, randm.size)
        System.arraycopy(encrypted, 8, commandData, 9, 8)
        Arrays.reverse(commandData, 9, 16)

        val wCmd = Command.newInstance()
        wCmd.type = Command.CommandType.WRITE
        wCmd.data = commandData
        wCmd.serviceUUID = serviceUUID
        wCmd.characteristicUUID = characteristicUUID
        wCmd.tag = TAG_LOGIN_WRITE

        val rCmd = Command.newInstance()
        rCmd.type = Command.CommandType.READ
        rCmd.serviceUUID = serviceUUID
        rCmd.characteristicUUID = characteristicUUID
        rCmd.tag = TAG_LOGIN_READ

        sendCommand(loginCallback, wCmd)
        sendCommand(loginCallback, rCmd)
    }

    fun reset(meshName: ByteArray?, password: ByteArray?, longTermKey: ByteArray?) {
        var longTermKey = longTermKey
        BleLog.d("prepare update mesh info")

        synchronized(this) {
            if (!isLogin) {
                dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "not login"))
                return
            }
        }

        currentLight.meshChanged = false

        newMeshName = meshName!!
        newPassword = password!!

        if (longTermKey == null) {
            longTermKey = Manufacture.default.factoryLtk
        }

        newLongTermKey = longTermKey

        if (resetDeviceAddress())
            return

        mDelayHandler.removeCallbacksAndMessages(null)

        val nn: ByteArray?
        val pwd: ByteArray?
        val ltk: ByteArray?

        try {
            nn = AES.encrypt(sessionKey, meshName)
            pwd = AES.encrypt(sessionKey, password)
            ltk = AES.encrypt(sessionKey, longTermKey)

            Arrays.reverse(nn!!, 0, nn.size - 1)
            Arrays.reverse(pwd!!, 0, pwd.size - 1)
            Arrays.reverse(ltk!!, 0, ltk.size - 1)

        } catch (e: InvalidKeyException) {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, e.message!!))
            return
        } catch (e: NoSuchAlgorithmException) {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, e.message!!))
            return
        } catch (e: NoSuchPaddingException) {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, e.message!!))
            return
        } catch (e: UnsupportedEncodingException) {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, e.message!!))
            return
        } catch (e: IllegalBlockSizeException) {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, e.message!!))
            return
        } catch (e: BadPaddingException) {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, e.message!!))
            return
        } catch (e: NoSuchProviderException) {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, e.message!!))
            return
        }

        val nnData = ByteArray(17)
        nnData[0] = Opcode.BLE_GATT_OP_PAIR_NETWORK_NAME.getValue()
        System.arraycopy(nn, 0, nnData, 1, nn.size)

        val pwdData = ByteArray(17)
        pwdData[0] = Opcode.BLE_GATT_OP_PAIR_PASS.getValue()
        System.arraycopy(pwd, 0, pwdData, 1, pwd.size)

        val ltkData = ByteArray(17)
        ltkData[0] = Opcode.BLE_GATT_OP_PAIR_LTK.getValue()
        System.arraycopy(ltk, 0, ltkData, 1, ltk.size)

        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val pairUUID = manufacture.getUUID(Manufacture.UUIDType.PAIR)

        val nnCmd = Command(serviceUUID, pairUUID, Command.CommandType.WRITE, nnData, TAG_RESET_MESH_NAME)
        val pwdCmd = Command(serviceUUID, pairUUID, Command.CommandType.WRITE, pwdData, TAG_RESET_MESH_PASSWORD)
        val ltkCmd = Command(serviceUUID, pairUUID, Command.CommandType.WRITE, ltkData, TAG_RESET_MESH_LTK)
        val checkCmd = Command(serviceUUID, pairUUID, Command.CommandType.READ, null, TAG_RESET_MESH_CHECK)

        sendCommand(resetCallback, nnCmd)
        sendCommand(resetCallback, pwdCmd)
        sendCommand(resetCallback, ltkCmd)
        sendCommand(resetCallback, checkCmd)
    }

    private fun resetDeviceAddress(): Boolean {

        val newAddress = currentLight.newMeshAddress
        val oldAddress = currentLight.meshAddress

        BleLog.d("mesh address -->$newAddress : $oldAddress")

        if (newAddress == oldAddress)
            return false

        enableNotification(notifyCallback, TAG_RESET_MESH_ADDRESS_NOTIFY_DATA)
        val opcode = 0xE0.toByte()
        val params = byteArrayOf((newAddress and 0xFF).toByte(), (newAddress shr 8 and 0xFF).toByte())

        BleLog.d("prepare update mesh address -->" + currentLight.macAddress + " src : " + Integer.toHexString(oldAddress) + " new : " + Integer.toHexString(newAddress))

        sendCommand(normalCallback, opcode, 0x0000, params, false, TAG_RESET_MESH_ADDRESS, 0)
        val params1 = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        sendCommand(normalCallback, opcode, 0x0000, params1, false, TAG_NORMAL_COMMAND, 0)
        mDelayHandler.postDelayed(mAllocAddressTask, 3000)

        return true
    }


    private fun enableNotification(callback: Command.Callback, tag: Any) {
        synchronized(this) {
            if (!isLogin)
                return
        }

        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.NOTIFY)

        val enableNotifyCmd = Command.newInstance()
        enableNotifyCmd.type = Command.CommandType.ENABLE_NOTIFY
        enableNotifyCmd.serviceUUID = serviceUUID
        enableNotifyCmd.characteristicUUID = characteristicUUID
        enableNotifyCmd.tag = tag

        sendCommand(callback, enableNotifyCmd)
    }

    fun enableNotification() {
        enableNotification(notifyCallback, TAG_NOTIFY_ENABLE)
    }

    fun disableNotification() {

        synchronized(this) {
            if (!isLogin)
                return
        }

        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.NOTIFY)

        val disableNotifyCmd = Command.newInstance()
        disableNotifyCmd.type = Command.CommandType.DISABLE_NOTIFY
        disableNotifyCmd.serviceUUID = serviceUUID
        disableNotifyCmd.characteristicUUID = characteristicUUID
        disableNotifyCmd.tag = TAG_NOTIFY_DISABLE

        sendCommand(notifyCallback, disableNotifyCmd)
    }

    fun updateNotification(data: ByteArray) {

        synchronized(this) {
            if (!isLogin)
                return
        }

        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.NOTIFY)

        val updateNotifyCmd = Command.newInstance()
        updateNotifyCmd.type = Command.CommandType.WRITE
        updateNotifyCmd.data = data
        updateNotifyCmd.serviceUUID = serviceUUID
        updateNotifyCmd.characteristicUUID = characteristicUUID
        updateNotifyCmd.tag = TAG_NOTIFY_UPDATE
        updateNotifyCmd.delay = DEFAULT_DELAY_TIME

        sendCommand(null, updateNotifyCmd)
        BleLog.d("LightController#updateNotification")
    }

    fun updateNotification() {
        val data = byteArrayOf(0x01)
        updateNotification(data)
    }


    fun delete() {

        synchronized(this) {
            if (!isLogin) {
                dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, "not login"))
                return
            }
        }

        val plaintext = ByteArray(16)

        for (i in 0..15) {
            plaintext[i] = meshName[i] xor password[i]
        }

        val randm = ByteArray(8)
        val sk = ByteArray(16)

        generateRandom(randm)

        System.arraycopy(randm, 0, sk, 0, randm.size)

        val encrypted: ByteArray?

        try {
            encrypted = AES.encrypt(sk, plaintext)
        } catch (e: InvalidKeyException) {
            dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, e.message!!))
            return
        } catch (e: NoSuchAlgorithmException) {
            dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, e.message!!))
            return
        } catch (e: NoSuchPaddingException) {
            dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, e.message!!))
            return
        } catch (e: UnsupportedEncodingException) {
            dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, e.message!!))
            return
        } catch (e: IllegalBlockSizeException) {
            dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, e.message!!))
            return
        } catch (e: BadPaddingException) {
            dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, e.message!!))
            return
        } catch (e: NoSuchProviderException) {
            dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, e.message!!))
            return
        }

        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.PAIR)

        val commandData = ByteArray(17)

        commandData[0] = Opcode.BLE_GATT_OP_PAIR_DELETE.getValue()

        System.arraycopy(randm, 0, commandData, 1, randm.size)
        System.arraycopy(encrypted!!, 8, commandData, 9, 8)

        Arrays.reverse(commandData, 9, 16)

        val wCmd = Command.newInstance()
        wCmd.serviceUUID = serviceUUID
        wCmd.characteristicUUID = characteristicUUID
        wCmd.type = Command.CommandType.WRITE
        wCmd.data = commandData
        wCmd.tag = TAG_DELETE_WRITE

        val rCmd = Command.newInstance()
        rCmd.serviceUUID = serviceUUID
        rCmd.characteristicUUID = characteristicUUID
        rCmd.type = Command.CommandType.READ
        rCmd.tag = TAG_DELETE_READ

        sendCommand(deleteCallback, wCmd)
        sendCommand(deleteCallback, rCmd)
    }


    fun startOta(firmware: ByteArray) {

        synchronized(this) {
            if (!isLogin) {
                dispatchEvent(LightEvent(LightEvent.OTA_FAILURE, "not login"))
                return
            }
        }

        BleLog.d("Start OTA")
        resetOta()
        otaPacketParser.set(firmware)
        sendNextOtaPacketCommand()
    }

    private fun resetOta() {
        mDelayHandler.removeCallbacksAndMessages(null)
        mDelayHandler.removeCallbacks(otaTask)
        otaPacketParser.clear()
    }

    private fun setOtaProgressChanged() {
        if (otaPacketParser.invalidateProgress()) {
            dispatchEvent(LightEvent(LightEvent.OTA_PROGRESS))
        }
    }

    private fun sendNextOtaPacketCommand(): Boolean {
        var isLast = false

        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.OTA)

        val cmd = Command.newInstance()
        cmd.serviceUUID = serviceUUID
        cmd.characteristicUUID = characteristicUUID
        cmd.type = Command.CommandType.WRITE_NO_RESPONSE

        if (otaPacketParser.hasNextPacket()) {
            cmd.data = otaPacketParser.nextPacket
            cmd.tag = TAG_OTA_WRITE
        } else {
            cmd.data = otaPacketParser.checkPacket
            cmd.tag = TAG_OTA_LAST
            isLast = true
        }

        sendCommand(otaCallback, cmd)

        return isLast
    }

    private fun validateOta(): Boolean {
        val sectionSize = Manufacture.default.otaSize
        val sendTotal = otaPacketParser.nextPacketIndex * 16
        BleLog.d("ota onCommandSampled byte length : " + sendTotal)
        if (sendTotal > 0 && sendTotal % sectionSize == 0) {
            BleLog.d("onCommandSampled ota read packet " + otaPacketParser.nextPacketIndex)
            val manufacture = Manufacture.default
            val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
            val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.OTA)

            val cmd = Command.newInstance()
            cmd.serviceUUID = serviceUUID
            cmd.characteristicUUID = characteristicUUID
            cmd.type = Command.CommandType.READ
            cmd.tag = TAG_OTA_READ
            sendCommand(otaCallback, cmd)
            return true
        }

        return false
    }

    private fun sendOtaCheckPacket() {
        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.OTA)

        val cmd = Command.newInstance()
        cmd.serviceUUID = serviceUUID
        cmd.characteristicUUID = characteristicUUID
        cmd.type = Command.CommandType.READ
        cmd.tag = TAG_OTA_CHECK
        cmd.delay = 0
        sendCommand(otaCallback, cmd)
    }

    private fun sendCommand(callback: Command.Callback, commandData: ByteArray, noResponse: Boolean, tag: Any, delay: Int): Boolean {

        val sk = sessionKey
        val sn = sequenceNumber

        BleLog.d("LightController#sendCommand#NoEncrypt: " + Arrays.bytesToHexString(commandData, ":"))
        val macAddress = currentLight.getMacBytes()
        val nonce = getSecIVM(macAddress, sn)
        val data = AES.encrypt(sk, nonce, commandData)

        val manufacture = Manufacture.default
        val serviceUUID = manufacture.getUUID(Manufacture.UUIDType.SERVICE)
        val characteristicUUID = manufacture.getUUID(Manufacture.UUIDType.COMMAND)

        val command = Command.newInstance()
        command.type = if (noResponse) Command.CommandType.WRITE_NO_RESPONSE else Command.CommandType.WRITE
        command.data = data
        command.serviceUUID = serviceUUID
        command.characteristicUUID = characteristicUUID
        command.tag = tag
        command.delay = delay

        return sendCommand(callback, command)
    }

    private fun sendCommand(callback: Command.Callback?, cmd: Command): Boolean {

        var success = true

        synchronized(this) {
            if (!isLogin) {
                success = false
            }
        }

        currentLight.sendCommand(callback!!, cmd)

        return success
    }

    fun sendCommand(callback: Command.Callback, opcode: Byte, address: Int, params: ByteArray?, noResponse: Boolean, tag: Any, delay: Int): Boolean {

        val sn = generateSequenceNumber()

        val command = ByteArray(20)

        var offset = 0

        // SN
        command[offset++] = (sn and 0xFF).toByte()
        command[offset++] = (sn shr 8 and 0xFF).toByte()
        command[offset++] = (sn shr 16 and 0xFF).toByte()

        // src address
        command[offset++] = 0x00
        command[offset++] = 0x00

        // dest address
        command[offset++] = (address and 0xFF).toByte()
        command[offset++] = (address shr 8 and 0xFF).toByte()

        // opcode
        command[offset++] = (opcode.toInt() or 0xC0).toByte()

        val manufacture = Manufacture.default
        val vendorId = manufacture.vendorId

        // vendor Id
        command[offset++] = (vendorId shr 8 and 0xFF).toByte()
        command[offset++] = (vendorId and 0xFF).toByte()

        // params
        if (params != null) {
            System.arraycopy(params, 0, command, offset, params.size)
        }

        return sendCommand(callback, command, noResponse, tag, delay)
    }

    fun sendCommand(opcode: Byte, address: Int, params: ByteArray, noResponse: Boolean, delay: Int): Boolean {
        return sendCommand(opcode, address, params, noResponse, TAG_NORMAL_COMMAND, delay)
    }

    fun sendCommand(opcode: Byte, address: Int, params: ByteArray, noResponse: Boolean, tag: Any, delay: Int): Boolean {
        return sendCommand(normalCallback, opcode, address, params, noResponse, tag, delay)
    }


    fun requestFirmware(): Boolean {

        val serviceUUID = UuidInformation.SERVICE_DEVICE_INFORMATION.value
        val characteristicUUID = UuidInformation.CHARACTERISTIC_FIRMWARE.value

        val cmd = Command.newInstance()
        cmd.serviceUUID = serviceUUID
        cmd.characteristicUUID = characteristicUUID
        cmd.type = Command.CommandType.READ

        return sendCommand(firmwareCallback, cmd)
    }


    private fun generateSequenceNumber(): Int {

        val maxNum = 0xFFFFFF

        if (sequenceNumber > maxNum)
            sequenceNumber = Math.round(Math.random().toFloat() * (maxNum - 1)) + 1

        sequenceNumber++

        return sequenceNumber
    }

    private fun generateRandom(randm: ByteArray): ByteArray {
        random.nextBytes(randm)
        return randm
    }

    private fun getSecIVM(meshAddress: ByteArray, sn: Int): ByteArray {

        val ivm = ByteArray(8)

        System.arraycopy(meshAddress, 0, ivm, 0, meshAddress.size)

        ivm[4] = 0x01
        ivm[5] = (sn and 0xFF).toByte()
        ivm[6] = (sn shr 8 and 0xFF).toByte()
        ivm[7] = (sn shr 16 and 0xFF).toByte()

        return ivm
    }

    private fun getSecIVS(macAddress: ByteArray): ByteArray {

        val ivs = ByteArray(8)

        ivs[0] = macAddress[0]
        ivs[1] = macAddress[1]
        ivs[2] = macAddress[2]

        return ivs
    }

    @Throws(Exception::class)
    private fun getSessionKey(meshName: ByteArray?, password: ByteArray?, randm: ByteArray, rands: ByteArray, sk: ByteArray): ByteArray? {

        val key = ByteArray(16)

        System.arraycopy(rands, 0, key, 0, rands.size)

        val plaintext = ByteArray(16)

        for (i in 0..15) {
            plaintext[i] = (meshName!![i] xor password!![i]).toByte()
        }

        val encrypted = AES.encrypt(key, plaintext)
        val result = ByteArray(16)

        System.arraycopy(rands, 0, result, 0, rands.size)
        System.arraycopy(encrypted, 8, result, 8, 8)
        Arrays.reverse(result, 8, 15)

        if (!Arrays.equals(result, sk))
            return null

        System.arraycopy(randm, 0, key, 0, randm.size)
        System.arraycopy(rands, 0, key, 8, rands.size)

        val sessionKey = AES.encrypt(plaintext, key)
        Arrays.reverse(sessionKey!!, 0, sessionKey.size - 1)

        return sessionKey
    }


    override fun onConnect(light: LightPeripheral) {
        BleLog.d("LightController#onConnect")
        if (isN && mIsUpdatingMesh) {
            BleLog.d("mDelayHandler#nCheckRunnable")
            mDelayHandler.removeCallbacks(mConnectTask)
            mDelayHandler.postDelayed(nCheckRunnable, (N_TIMEOUT * 1000).toLong())
            isChecking = false
        }
    }

    override fun onDisconnect(light: LightPeripheral) {

        BleLog.d("LightController.onDisconnect")
        disconnect()
        if (isN && mIsUpdatingMesh && isChecking) {
            isChecking = false
            mDelayHandler.removeCallbacks(nCheckRunnable)
            failCount++
            BleLog.d("fail count:" + failCount)
            if (failCount >= MAX_RETRY) {
                BleLog.d("LightController.onDisconnect.CONNECT_FAILURE_N")
                dispatchEvent(LightEvent(LightEvent.CONNECT_FAILURE_N, " onDisconnect " + light.macAddress))
            } else {
                retryConnect()
            }
        } else {
            dispatchEvent(LightEvent(LightEvent.CONNECT_FAILURE, " onDisconnect " + light.macAddress))
        }
    }

    override fun onServicesDiscovered(light: LightPeripheral, services: List<BluetoothGattService>) {
        if (isN && mIsUpdatingMesh && services.isEmpty()) {
            disconnect()
        } else {
            mDelayHandler.removeCallbacks(nCheckRunnable)
            dispatchEvent(LightEvent(LightEvent.CONNECT_SUCCESS))
        }
    }

    override fun onNotify(light: LightPeripheral, data: ByteArray, serviceUUID: UUID?, characteristicUUID: UUID?, tag: Any?) {

        val macAddress = light.getMacBytes()
        val nonce = getSecIVS(macAddress)
        System.arraycopy(data, 0, nonce, 3, 5)
        val result = AES.decrypt(sessionKey, nonce, data)

        BleLog.d("Notify Data --> " + Arrays.bytesToHexString(result, ","))

        if (tag != null) {
            onDeviceAddressNotify(data, tag)
        }
        dispatchEvent(LightEvent(LightEvent.NOTIFICATION_RECEIVE, result))
    }

    private fun onDeviceAddressNotify(data: ByteArray, tag: Any) {

        if (tag != TAG_RESET_MESH_ADDRESS_NOTIFY_DATA)
            return

        val length = data.size
        val minLength = 20
        var position = 7

        if (length < minLength)
            return

        val opcode = data[position++].toInt() and 0xFF
        val vendorId = (data[position++].toInt() shl 8) + data[position++]

        if (vendorId != Manufacture.default.vendorId)
            return

        if (opcode != 0xE1)
            return

        val meshAddress = data[10] + (data[11].toInt() shl 8)

        if (meshAddress == currentLight.meshAddress)
            return

        currentLight.meshAddress = meshAddress

        BleLog.d("Device Address Update Success --> old : " + Integer.toHexString(currentLight.meshAddress) + " new: " + Integer.toHexString(meshAddress))

        reset(newMeshName, newPassword, newLongTermKey)
    }

    override fun onRssiChanged(light: LightPeripheral) {
        dispatchEvent(LightEvent(LightEvent.RSSI_CHANGED))
    }


    class LightEvent : Event<Int> {

        var args: Any? = null

        constructor(type: Int?, args: Any) : super(null, type) {
            this.args = args
        }

        constructor(type: Int?) : super(null, type)

        companion object {

            val LOGIN_SUCCESS = 0
            val LOGIN_FAILURE = 1
            val CONNECT_SUCCESS = 3
            val CONNECT_FAILURE = 4
            val CONNECT_FAILURE_N = 5// android N
            val RESET_MESH_SUCCESS = 10
            val RESET_MESH_FAILURE = 11
            val ENABLE_NOTIFICATION_SUCCESS = 20
            val ENABLE_NOTIFICATION_FAILURE = 21
            val NOTIFICATION_RECEIVE = 22
            val GET_LTK_SUCCESS = 30
            val GET_LTK_FAILURE = 31
            val DELETE_SUCCESS = 40
            val DELETE_FAILURE = 41
            val COMMAND_SUCCESS = 50
            val COMMAND_FAILURE = 51
            val RSSI_CHANGED = 60
            val OTA_SUCCESS = 71
            val OTA_FAILURE = 72
            val OTA_PROGRESS = 73
            val GET_FIRMWARE_SUCCESS = 80
            val GET_FIRMWARE_FAILURE = 81
        }
    }

    private inner class NCheckRunnable : Runnable {
        override fun run() {
            synchronized(this@LightController) {
                if (!this@LightController.isLogin) {
                    BleLog.d("LightController.Connection Timeout N")
                    disconnect()
                    isChecking = true
                }
            }
        }
    }


    private inner class ConnectionRunnable : Runnable {
        override fun run() {
            synchronized(this@LightController) {
                if (!this@LightController.isLogin) {
                    BleLog.d("LightController.Connection Timeout")
                    disconnect()
                    this@LightController.dispatchEvent(LightEvent(LightEvent.CONNECT_FAILURE, "connection timeout"))
                }
            }
        }
    }

    private inner class LoginCommandCallback : Command.Callback {

        override fun success(peripheral: Peripheral, command: Command, response: Any) {

            if (command.tag != TAG_LOGIN_READ)
                return

            val data = response as ByteArray

            if (data[0] == Opcode.BLE_GATT_OP_PAIR_ENC_FAIL.getValue()) {
                disconnect()
                dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, "encryption is not correct"))
                return
            }

            val sk = ByteArray(16)
            val rands = ByteArray(8)

            System.arraycopy(data, 1, sk, 0, 16)
            System.arraycopy(data, 1, rands, 0, 8)

            try {

                sessionKey = getSessionKey(meshName, password, loginRandm, rands, sk)!!

                if (sessionKey == null) {
                    disconnect()
                    dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, "sessionKey invalid"))
                    return
                }

                synchronized(this@LightController) {
                    isLogin = true
                }

                mDelayHandler.removeCallbacks(mConnectTask)
                mDelayHandler.removeCallbacksAndMessages(null)
                dispatchEvent(LightEvent(LightEvent.LOGIN_SUCCESS))

            } catch (e: Exception) {
                disconnect()
                dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, e.message!!))
            }

        }

        override fun error(peripheral: Peripheral, command: Command,
                           reason: String) {
            BleLog.d("login command error  : " + reason)

            disconnect()
            dispatchEvent(LightEvent(LightEvent.LOGIN_FAILURE, reason))
        }

        override fun timeout(peripheral: Peripheral, command: Command): Boolean {
            return false
        }
    }

    private inner class SetDeviceAddressRunnable : Runnable {

        override fun run() {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set device address timeout"))
        }
    }

    private inner class ResetCommandCallback : Command.Callback {

        override fun success(peripheral: Peripheral, command: Command, response: Any) {

            if (command.tag != TAG_RESET_MESH_CHECK)
                return

            val data = response as ByteArray

            if (data[0] == Opcode.BLE_GATT_OP_PAIR_CONFIRM.getValue()) {

                try {

                    var sk: ByteArray? = ByteArray(16)

                    for (i in 0..15) {
                        sk!![i] = (newMeshName[i].toInt() xor newPassword[i].toInt() xor newLongTermKey[i].toInt()).toByte()
                    }

                    sk = AES.encrypt(sessionKey, sk)
                    sk = Arrays.reverse(sk)

                    val sk1 = ByteArray(16)
                    System.arraycopy(data, 1, sk1, 0, 16)

                    if (!Arrays.equals(sk, sk1)) {
                        currentLight.meshChanged = false
                        dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
                        return
                    }

                } catch (e: InvalidKeyException) {
                    currentLight.meshChanged = false
                    dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
                    return
                } catch (e: IllegalBlockSizeException) {
                    currentLight.meshChanged = false
                    dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
                    return
                } catch (e: BadPaddingException) {
                    currentLight.meshChanged = false
                    dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
                    return
                } catch (e: NoSuchAlgorithmException) {
                    currentLight.meshChanged = false
                    dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
                    return
                } catch (e: NoSuchPaddingException) {
                    currentLight.meshChanged = false
                    dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
                    return
                } catch (e: NoSuchProviderException) {
                    currentLight.meshChanged = false
                    dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
                    return
                } catch (e: UnsupportedEncodingException) {
                    currentLight.meshChanged = false
                    dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
                    return
                }

                meshName = newMeshName
                password = newPassword
                longTermKey = newLongTermKey

                val light = peripheral as LightPeripheral
                light.setMeshName(meshName)
                light.password = newPassword
                light.longTermKey = newLongTermKey
                light.meshChanged = true
                dispatchEvent(LightEvent(LightEvent.RESET_MESH_SUCCESS))

            } else {
                currentLight.meshChanged = false
                dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set mesh failure"))
            }
        }

        override fun error(peripheral: Peripheral, command: Command, reason: String) {
            dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, reason))
        }

        override fun timeout(peripheral: Peripheral, command: Command): Boolean {
            return false
        }
    }

    private inner class NotifyCommandCallback : Command.Callback {

        override fun success(peripheral: Peripheral, command: Command, data: Any) {

        }

        override fun error(peripheral: Peripheral, command: Command, reason: String) {

            if (command.tag == TAG_RESET_MESH_ADDRESS_NOTIFY_ENABLE || command.tag == TAG_RESET_MESH_ADDRESS_NOTIFY_DATA) {
                dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set address fail"))
            } else {
                dispatchEvent(LightEvent(LightEvent.ENABLE_NOTIFICATION_FAILURE, reason))
            }
        }

        override fun timeout(peripheral: Peripheral, command: Command): Boolean {
            return false
        }
    }


    private inner class DeleteCommandCallback : Command.Callback {

        override fun success(peripheral: Peripheral, command: Command, `object`: Any) {
            if (command.tag != TAG_DELETE_READ)
                return
            dispatchEvent(LightEvent(LightEvent.DELETE_SUCCESS))
        }

        override fun error(peripheral: Peripheral, command: Command, reason: String) {
            dispatchEvent(LightEvent(LightEvent.DELETE_FAILURE, reason))
        }

        override fun timeout(peripheral: Peripheral, command: Command): Boolean {
            return false
        }
    }

    private inner class OtaCommandCallback : Command.Callback {

        override fun success(peripheral: Peripheral, command: Command, obj: Any) {
            when {
                command.tag == TAG_OTA_WRITE -> {
                    val delay = Manufacture.default.otaDelay
                    if (delay <= 0) {
                        if (!validateOta())
                            sendNextOtaPacketCommand()
                    } else {
                        mDelayHandler.postDelayed(otaTask, delay.toLong())
                    }
                    setOtaProgressChanged()
                }
                command.tag == TAG_OTA_READ -> {
                    BleLog.d("read response : " + Arrays.bytesToString(obj as ByteArray))
                    sendNextOtaPacketCommand()
                }
                command.tag == TAG_OTA_CHECK -> {
                    BleLog.d("last read packet response : " + Arrays.bytesToString(obj as ByteArray))
                    resetOta()
                    setOtaProgressChanged()
                    dispatchEvent(LightEvent(LightEvent.OTA_SUCCESS))
                }
                command.tag == TAG_OTA_LAST -> sendOtaCheckPacket()
            }
        }

        override fun error(peripheral: Peripheral, command: Command, errorMsg: String) {
            BleLog.d("error packet : " + Arrays.bytesToHexString(command.data, ":"))
            if (command.tag == TAG_OTA_CHECK) {
                BleLog.d("last read packet response error : ")
                resetOta()
                setOtaProgressChanged()
                dispatchEvent(LightEvent(LightEvent.OTA_SUCCESS))
            } else {
                resetOta()
                dispatchEvent(LightEvent(LightEvent.OTA_FAILURE))
            }
        }

        override fun timeout(peripheral: Peripheral, command: Command): Boolean {
            if (command.tag == TAG_OTA_CHECK) {
                BleLog.d("last read packet response timeout : ")
                resetOta()
                setOtaProgressChanged()
                dispatchEvent(LightEvent(LightEvent.OTA_SUCCESS))
                return false
            } else if (command.tag == TAG_OTA_READ) {
                sendNextOtaPacketCommand()
                return false
            }
            BleLog.d("timeout : " + Arrays.bytesToHexString(command.data, ":"))
            return false
        }
    }

    private inner class FirmwareCallback : Command.Callback {

        override fun success(peripheral: Peripheral, command: Command, obj: Any) {
            val light = peripheral as LightPeripheral
            light.putCharacteristicValue(command.characteristicUUID!!, obj as ByteArray)
            dispatchEvent(LightEvent(LightEvent.GET_FIRMWARE_SUCCESS))
        }

        override fun error(peripheral: Peripheral, command: Command, errorMsg: String) {
            dispatchEvent(LightEvent(LightEvent.GET_FIRMWARE_FAILURE))
        }

        override fun timeout(peripheral: Peripheral, command: Command): Boolean {
            return false
        }
    }

    private inner class NormalCommandCallback : Command.Callback {

        override fun success(peripheral: Peripheral, command: Command, obj: Any) {
            dispatchEvent(LightEvent(LightEvent.COMMAND_SUCCESS, command))
        }

        override fun error(peripheral: Peripheral, command: Command, reason: String) {
            if (command.tag == TAG_RESET_MESH_ADDRESS) {
                dispatchEvent(LightEvent(LightEvent.RESET_MESH_FAILURE, "set address fail"))
            } else {
                dispatchEvent(LightEvent(LightEvent.COMMAND_FAILURE, command))
            }
        }

        override fun timeout(peripheral: Peripheral, command: Command): Boolean {
            return false
        }
    }

    private inner class OtaRunnable : Runnable {

        override fun run() {
            if (!validateOta())
                sendNextOtaPacketCommand()
        }
    }

    private inner class OtaPacketParser {

        private var total: Int = 0
        private var index = -1
        private var data: ByteArray? = null
        var progress: Int = 0
            private set

        val nextPacketIndex: Int
            get() = index + 1

        val nextPacket: ByteArray
            get() {

                val index = nextPacketIndex
                val packet = getPacket(index)
                this.index = index

                return packet
            }

        val checkPacket: ByteArray
            get() {
                val packet = ByteArray(4)
                val index = nextPacketIndex
                fillIndex(packet, index)
                val crc = crc16(packet)
                fillCrc(packet, crc)
                BleLog.d("ota check packet ---> index : " + index + " crc : " + crc + " content : " + Arrays.bytesToHexString(packet, ":"))
                return packet
            }

        fun set(data: ByteArray) {
            clear()

            this.data = data
            val length = data.size
            val size = 16

            total = if (length % size == 0) {
                length / size
            } else {
                Math.floor((length / size + 1).toDouble()).toInt()
            }
        }

        fun clear() {
            progress = 0
            total = 0
            index = -1
            data = null
        }

        fun hasNextPacket(): Boolean {
            return total > 0 && index + 1 < total
        }

        fun getPacket(index: Int): ByteArray {

            val length = data!!.size
            val size = 16
            var packetSize = if (length > size) {
                if (index + 1 == total) {
                    length - index * size
                } else {
                    size
                }
            } else {
                length
            }

            packetSize += 4
            val packet = ByteArray(packetSize)

            System.arraycopy(data!!, index * size, packet, 2, packetSize - 4)

            fillIndex(packet, index)
            val crc = crc16(packet)
            fillCrc(packet, crc)
            BleLog.d("ota packet ---> index : " + index + " total : " + total + " crc : " + crc + " content : " + Arrays.bytesToHexString(packet, ":"))
            return packet
        }

        fun fillIndex(packet: ByteArray, index: Int) {
            var offset = 0
            packet[offset++] = (index and 0xFF).toByte()
            packet[offset] = (index shr 8 and 0xFF).toByte()
        }

        fun fillCrc(packet: ByteArray, crc: Int) {
            var offset = packet.size - 2
            packet[offset++] = (crc and 0xFF).toByte()
            packet[offset] = (crc shr 8 and 0xFF).toByte()
        }

        fun crc16(packet: ByteArray): Int {

            val length = packet.size - 2
            val poly = shortArrayOf(0, 0xA001.toShort())
            var crc = 0xFFFF
            var ds: Int

            for (j in 0 until length) {

                ds = packet[j].toInt()

                for (i in 0..7) {
                    crc = crc shr 1 xor (poly[crc xor ds and 1].toInt() and 0xFFFF)
                    ds = ds shr 1
                }
            }

            return crc
        }

        fun invalidateProgress(): Boolean {

            val a = nextPacketIndex.toFloat()
            val b = total.toFloat()

            val progress = Math.floor((a / b * 100).toDouble()).toInt()

            if (this.progress == progress)
                return false

            this.progress = progress

            return true
        }
    }

    companion object {

        private val TAG_LOGIN_WRITE = 1
        private val TAG_LOGIN_READ = 2

        private val TAG_RESET_MESH_NAME = 101
        private val TAG_RESET_MESH_PASSWORD = 102
        private val TAG_RESET_MESH_LTK = 103
        private val TAG_RESET_MESH_CHECK = 104
        private val TAG_RESET_MESH_ADDRESS = 105
        private val TAG_RESET_MESH_ADDRESS_NOTIFY_ENABLE = 106
        private val TAG_RESET_MESH_ADDRESS_NOTIFY_DATA = 107

        private val TAG_NOTIFY_ENABLE = 201
        private val TAG_NOTIFY_DISABLE = 203
        private val TAG_NOTIFY_UPDATE = 204

        private val TAG_GET_LTK_WRITE = 301
        private val TAG_GET_LTK_READ = 302

        private val TAG_DELETE_WRITE = 401
        private val TAG_DELETE_READ = 402

        private val TAG_OTA_WRITE = 501
        private val TAG_OTA_LAST = 502
        private val TAG_OTA_READ = 503
        private val TAG_OTA_CHECK = 504

        private val TAG_NORMAL_COMMAND = 1000

        // 控制指令 默认320ms延时
        private val DEFAULT_DELAY_TIME = 320
        private val N_TIMEOUT = 5
        private val MAX_RETRY = 3
    }
}
