package com.telink.bluetooth.light

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.telink.util.Arrays
import com.telink.util.BleLog
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

open class Peripheral(device: BluetoothDevice, protected var scanRecord: ByteArray, rssi: Int) : BluetoothGattCallback() {

    private val mInputCommandQueue: Queue<CommandContext> = ConcurrentLinkedQueue()
    private val mOutputCommandQueue: Queue<CommandContext> = ConcurrentLinkedQueue()
    private val mNotificationCallbacks: MutableMap<String, CommandContext> = ConcurrentHashMap()

    private val mTimeoutHandler = Handler(Looper.getMainLooper())
    private val mRssiUpdateHandler = Handler(Looper.getMainLooper())
    private val mDelayHandler = Handler(Looper.getMainLooper())
    private val mRssiUpdateRunnable: Runnable = RssiUpdateRunnable()
    private val mCommandTimeoutRunnable: Runnable = CommandTimeoutRunnable()
    private val mCommandDelayRunnable: Runnable = CommandDelayRunnable()

    private val mStateLock = Any()
    private val mProcessLock = Any()


    var device: BluetoothDevice
        private set
    private lateinit var gatt: BluetoothGatt
    var rssi: Int = 0
        private set
    var deviceName: String
        private set
    var macAddress: String
        private set
    private lateinit var macBytes: ByteArray
    var type: Int = 0
        private set
   lateinit var services: List<BluetoothGattService>


    private var processing = false

    private var monitorRssi = false
    private var updateIntervalMill = 5 * 1000
    private var commandTimeoutMill = 10 * 1000
    private var lastTime: Long = 0
    private var mConnState = CONN_STATE_IDLE

    val isConnected: Boolean
        get() = synchronized(mStateLock) {
            return mConnState == CONN_STATE_CONNECTED
        }

    init {
        this.device = device
        this.rssi = rssi
        this.deviceName = device.name
        this.macAddress = device.address
        this.type = device.type
    }

    fun getMacBytes(): ByteArray {
        if (macBytes == null) {
            val strArray = macAddress.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val length = strArray.size
            macBytes = ByteArray(length)
            for (i in 0 until length) {
                macBytes[i] = (Integer.parseInt(strArray[i], 16) and 0xFF).toByte()
            }
            Arrays.reverse(macBytes, 0, length - 1)
        }
        return macBytes
    }

    fun connect(context: Context) {
        synchronized(mStateLock) {
            lastTime = 0
            if (mConnState == CONN_STATE_IDLE) {
                BleLog.w("Peripheral#connect $deviceName  -- $macAddress")
                mConnState = CONN_STATE_CONNECTING
                gatt = device.connectGatt(context, false, this)
                if (gatt == null) {
                    disconnect()
                    mConnState = CONN_STATE_IDLE
                    BleLog.d("Peripheral# gatt NULL onDisconnect:$deviceName  -- $macAddress")
                    onDisconnect()
                }
            }
        }
    }

    open fun disconnect() {
        synchronized(mStateLock) {
            if (mConnState != CONN_STATE_CONNECTING && mConnState != CONN_STATE_CONNECTED)
                return
        }
        BleLog.w("disconnect $deviceName  -- $macAddress")
        clear()

        synchronized(mStateLock) {
            mConnState = if (gatt != null) {
                val connState = mConnState
                if (connState == CONN_STATE_CONNECTED) {
                    gatt.disconnect()
                    CONN_STATE_DISCONNECTING
                } else {
                    gatt.disconnect()
                    gatt.close()
                    CONN_STATE_CLOSED
                }
            } else {
                CONN_STATE_IDLE
            }
        }
    }

    private fun clear() {
        processing = false
        stopMonitoringRssi()
        cancelCommandTimeoutTask()
        mInputCommandQueue.clear()
        mOutputCommandQueue.clear()
        mNotificationCallbacks.clear()
        mDelayHandler.removeCallbacksAndMessages(null)
    }

    fun sendCommand(callback: Command.Callback, command: Command): Boolean {
        synchronized(mStateLock) {
            if (mConnState != CONN_STATE_CONNECTED)
                return false
        }
        val commandContext = CommandContext(callback, command)
        postCommand(commandContext)
        return true
    }

    fun startMonitoringRssi(interval: Int) {
        monitorRssi = true

        updateIntervalMill = if (interval <= 0)
            RSSI_UPDATE_TIME_INTERVAL
        else
            interval
    }

    private fun stopMonitoringRssi() {
        monitorRssi = false
        mRssiUpdateHandler.removeCallbacks(mRssiUpdateRunnable)
        mRssiUpdateHandler.removeCallbacksAndMessages(null)
    }

    fun requestConnectionPriority(connectionPriority: Int): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && gatt.requestConnectionPriority(connectionPriority)
    }



    protected open fun onConnect() {
        enableMonitorRssi(monitorRssi)
    }

    protected open fun onDisconnect() {
        enableMonitorRssi(false)
    }

    protected open fun onServicesDiscovered(services: List<BluetoothGattService>) {}

    protected open fun onNotify(data: ByteArray, serviceUUID: UUID?,  characteristicUUID: UUID?, tag: Any?) {
    }

    protected open fun onRssiChanged() {}

    private fun enableMonitorRssi(enable: Boolean) {

        if (enable) {
            mRssiUpdateHandler.removeCallbacks(mRssiUpdateRunnable)
            mRssiUpdateHandler.postDelayed(mRssiUpdateRunnable, updateIntervalMill.toLong())
        } else {
            mRssiUpdateHandler.removeCallbacks(mRssiUpdateRunnable)
            mRssiUpdateHandler.removeCallbacksAndMessages(null)
        }
    }



    private fun postCommand(commandContext: CommandContext) {
        if (commandContext.command!!.delay < 0) {
            synchronized(mOutputCommandQueue) {
                mOutputCommandQueue.add(commandContext)
                processCommand(commandContext)
            }
            return
        }

        mInputCommandQueue.add(commandContext)
        synchronized(mProcessLock) {
            if (!processing) {
                processCommand()
            }
        }
    }

    private fun processCommand() {
        var commandContext: CommandContext? = null
        val commandType: Command.CommandType

        synchronized(mInputCommandQueue) {
            if (mInputCommandQueue.isEmpty())
                return
            commandContext = mInputCommandQueue.poll()
        }

        if (commandContext == null)
            return

        commandType = commandContext?.command!!.type

        if (commandType !== Command.CommandType.ENABLE_NOTIFY && commandType !== Command.CommandType.DISABLE_NOTIFY) {
            synchronized(mOutputCommandQueue) {
                mOutputCommandQueue.add(commandContext)
            }
            synchronized(mProcessLock) {
                if ((!processing)!!)
                    processing = true
            }
        }

        val delay = commandContext?.command!!.delay
        if (delay > 0) {
            val currentTime = System.currentTimeMillis()
            if (lastTime == 0L || currentTime - lastTime >= delay)
                processCommand(commandContext!!)
            else
                mDelayHandler.postDelayed(mCommandDelayRunnable, delay.toLong())
        } else {
            processCommand(commandContext!!)
        }
    }

    @Synchronized private fun processCommand(commandContext: CommandContext) {

        val command = commandContext.command
        val commandType = command!!.type

        BleLog.d("processCommand : " + command.toString())

        when (commandType) {
            Command.CommandType.READ -> {
                postCommandTimeoutTask()
                readCharacteristic(commandContext, command.serviceUUID, command.characteristicUUID)
            }

            Command.CommandType.WRITE -> {
                postCommandTimeoutTask()
                writeCharacteristic(commandContext, command.serviceUUID, command.characteristicUUID,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,  command.data)
            }

            Command.CommandType.WRITE_NO_RESPONSE -> {
                postCommandTimeoutTask()
                writeCharacteristic(commandContext, command.serviceUUID,command.characteristicUUID,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,command.data)
            }

            Command.CommandType.ENABLE_NOTIFY -> enableNotification(commandContext, command.serviceUUID,
                    command.characteristicUUID)

            Command.CommandType.DISABLE_NOTIFY -> disableNotification(commandContext, command.serviceUUID,
                    command.characteristicUUID)
        }
    }

    private fun commandCompleted() {
        BleLog.w("commandCompleted")
        synchronized(mProcessLock) {
            if (processing)
                processing = false
        }
        processCommand()
    }

    private fun commandSuccess(commandContext: CommandContext?, data: Any?) {
        lastTime = System.currentTimeMillis()
        if (commandContext != null) {
            val command = commandContext.command
            val callback = commandContext.callback
            commandContext.clear()
            callback?.success(this, command!!, data!!)
        }
    }

    private fun commandSuccess(data: Any?) {
        val commandContext = mOutputCommandQueue.poll()
        commandSuccess(commandContext, data)
    }

    private fun commandError(commandContext: CommandContext?, errorMsg: String) {
        BleLog.e("commandError:" + errorMsg)
        lastTime = System.currentTimeMillis()
        if (commandContext != null) {
            val command = commandContext.command
            val callback = commandContext.callback
            commandContext.clear()
            callback?.error(this, command!!, errorMsg)
        }
    }

    private fun commandError(errorMsg: String) {
        val commandContext: CommandContext = mOutputCommandQueue.poll()
        commandError(commandContext, errorMsg)
    }

    private fun commandTimeout(commandContext: CommandContext?): Boolean {
        BleLog.w("commandTimeout")
        lastTime = System.currentTimeMillis()
        if (commandContext != null) {
            val command = commandContext.command
            val callback = commandContext.callback
            commandContext.clear()
            if (callback != null) {
                return callback.timeout(this, command!!)
            }
        }
        return false
    }

    private fun postCommandTimeoutTask() {
        if (commandTimeoutMill <= 0)
            return
        mTimeoutHandler.removeCallbacksAndMessages(null)
        mTimeoutHandler.postDelayed(mCommandTimeoutRunnable, commandTimeoutMill.toLong())
    }

    private fun cancelCommandTimeoutTask() {
        mTimeoutHandler.removeCallbacksAndMessages(null)
    }

    private fun readCharacteristic(commandContext: CommandContext, serviceUUID: UUID?, characteristicUUID: UUID?) {
        var success = true
        var errorMsg = ""
        val service = gatt.getService(serviceUUID)

        if (service != null) {
            val characteristic = service.getCharacteristic(characteristicUUID)
            if (characteristic != null) {
                if (!gatt.readCharacteristic(characteristic)) {
                    success = false
                    errorMsg = "read characteristic error"
                }
            } else {
                success = false
                errorMsg = "read characteristic error"
            }
        } else {
            success = false
            errorMsg = "service is not offered by the remote device"
        }

        if (!success) {
            commandError(errorMsg)
            commandCompleted()
        }
    }

    private fun writeCharacteristic(commandContext: CommandContext, serviceUUID: UUID?, characteristicUUID: UUID?, writeType: Int, data: ByteArray?) {
        var success = true
        var errorMsg = ""
        val service = gatt.getService(serviceUUID)

        if (service != null) {
            val characteristic = findWritableCharacteristic(service, characteristicUUID, writeType)
            if (characteristic != null) {
                characteristic.value = data
                characteristic.writeType = writeType
                if (!gatt.writeCharacteristic(characteristic)) {
                    success = false
                    errorMsg = "write characteristic error"
                }
            } else {
                success = false
                errorMsg = "no characteristic"
            }
        } else {
            success = false
            errorMsg = "service is not offered by the remote device"
        }
        if (!success) {
            commandError(errorMsg)
            commandCompleted()
        }
    }

    private fun enableNotification(commandContext: CommandContext, serviceUUID: UUID?, characteristicUUID: UUID?) {

        var success = true
        var errorMsg = ""
        val service = gatt.getService(serviceUUID)
        if (service != null) {
            val characteristic = findNotifyCharacteristic(service, characteristicUUID)
            if (characteristic != null) {
                if (!gatt.setCharacteristicNotification(characteristic,true)) {
                    success = false
                    errorMsg = "enable notification error"
                } else {
                    val key = generateHashKey(serviceUUID,characteristic)
                    mNotificationCallbacks.put(key, commandContext)
                }
            } else {
                success = false
                errorMsg = "no characteristic"
            }
        } else {
            success = false
            errorMsg = "service is not offered by the remote device"
        }
        if (!success) {
            commandError(commandContext, errorMsg)
        }
        commandCompleted()
    }

    private fun disableNotification(commandContext: CommandContext, serviceUUID: UUID?, characteristicUUID: UUID?) {

        var success = true
        var errorMsg = ""
        val service = gatt.getService(serviceUUID)
        if (service != null) {
            val characteristic = findNotifyCharacteristic(service, characteristicUUID)
            if (characteristic != null) {
                val key = generateHashKey(serviceUUID, characteristic)
                mNotificationCallbacks.remove(key)
                if (!gatt.setCharacteristicNotification(characteristic,false)) {
                    success = false
                    errorMsg = "disable notification error"
                }
            } else {
                success = false
                errorMsg = "no characteristic"
            }
        } else {
            success = false
            errorMsg = "service is not offered by the remote device"
        }
        if (!success) {
            commandError(commandContext, errorMsg)
        }
        commandCompleted()
    }

    private fun findWritableCharacteristic( service: BluetoothGattService, characteristicUUID: UUID?, writeType: Int): BluetoothGattCharacteristic? {

        var writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        }
        val characteristics = service.characteristics

        return characteristics.firstOrNull { it.properties and writeProperty != 0 && characteristicUUID == it.uuid }
    }

    private fun findNotifyCharacteristic( service: BluetoothGattService, characteristicUUID: UUID?): BluetoothGattCharacteristic? {

        val characteristics = service.characteristics
        var characteristic = characteristics.firstOrNull { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 && characteristicUUID == it.uuid }

        if (characteristic != null)
            return characteristic

        for (c in characteristics) {
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 && characteristicUUID == c.uuid) {
                characteristic = c
                break
            }
        }

        return characteristic
    }

    private fun generateHashKey(characteristic: BluetoothGattCharacteristic)= generateHashKey(characteristic.service.uuid, characteristic)

    private fun generateHashKey(serviceUUID: UUID?, characteristic: BluetoothGattCharacteristic): String {
        return (serviceUUID.toString() + "|" + characteristic.uuid + "|" + characteristic.instanceId)
    }



    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int,  newState: Int) {
        BleLog.d("onConnectionStateChange  status :$status  state : $newState")

        if (newState == BluetoothGatt.STATE_CONNECTED) {
            synchronized(mStateLock) {
                mConnState = CONN_STATE_CONNECTED
            }

            if (gatt == null || !gatt!!.discoverServices()) {
                BleLog.d("remote service discovery has been stopped status = $newState")
                disconnect()
            } else {
                onConnect()
            }
        } else {

            synchronized(mStateLock) {
                BleLog.d("Close")
                if (gatt != null) {
                    gatt.close()
                    mConnState = CONN_STATE_CLOSED
                }
                clear()
                mConnState = CONN_STATE_IDLE
                BleLog.d("Peripheral#onConnectionStateChange#onDisconnect")
                onDisconnect()
            }
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        super.onCharacteristicChanged(gatt, characteristic)
        val key = generateHashKey(characteristic)
        val commandContext = mNotificationCallbacks[key]
        if (commandContext != null) {
            onNotify(characteristic.value, commandContext.command!!.serviceUUID,
                    commandContext.command!!.characteristicUUID, commandContext.command!!.tag)
        }
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt,  characteristic: BluetoothGattCharacteristic, status: Int) {
        super.onCharacteristicRead(gatt, characteristic, status)
        cancelCommandTimeoutTask()

        if (status == BluetoothGatt.GATT_SUCCESS) {
            val data = characteristic.value
            commandSuccess(data)
        } else {
            commandError("read characteristic failed")
        }
        commandCompleted()
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        super.onCharacteristicWrite(gatt, characteristic, status)

        cancelCommandTimeoutTask()
        if (status == BluetoothGatt.GATT_SUCCESS) {
            commandSuccess(null)
        } else {
            commandError("write characteristic fail")
        }
        BleLog.d("onCharacteristicWrite newStatus : $status")
        commandCompleted()
    }

    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        super.onDescriptorRead(gatt, descriptor, status)

        cancelCommandTimeoutTask()
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val data = descriptor.value
            commandSuccess(data)
        } else {
            commandError("read description failed")
        }
        commandCompleted()
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        super.onDescriptorWrite(gatt, descriptor, status)

        cancelCommandTimeoutTask()
        if (status == BluetoothGatt.GATT_SUCCESS) {
            commandSuccess(null)
        } else {
            commandError("write description failed")
        }
        commandCompleted()
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val services = gatt.services
            this.services = services
            onServicesDiscovered(services)
        } else {
            BleLog.d("Service discovery failed")
            disconnect()
        }
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        super.onReadRemoteRssi(gatt, rssi, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (rssi != this.rssi) {
                this.rssi = rssi
                onRssiChanged()
            }
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        BleLog.d("mtu changed : $mtu")
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        super.onReliableWriteCompleted(gatt, status)
    }

    private inner class CommandContext(var callback: Command.Callback?, var command: Command?) {
        var time: Long = 0
        fun clear() {
            command = null
            callback = null
        }
    }

    private inner class RssiUpdateRunnable : Runnable {

        override fun run() {
            if (!monitorRssi)
                return
            if (!isConnected)
                return
            if (gatt != null)
                gatt.readRemoteRssi()
            mRssiUpdateHandler.postDelayed(mRssiUpdateRunnable, updateIntervalMill.toLong())
        }
    }

    private inner class CommandTimeoutRunnable : Runnable {

        override fun run() {
            synchronized(mOutputCommandQueue) {
                val commandContext = mOutputCommandQueue.peek()
                if (commandContext != null) {
                    val command = commandContext.command
                    val callback = commandContext.callback
                    val retry = commandTimeout(commandContext)
                    if (retry) {
                        commandContext.command = command
                        commandContext.callback = callback
                        processCommand(commandContext)
                    } else {
                        mOutputCommandQueue.poll()
                        commandCompleted()
                    }
                }
            }
        }
    }

    private inner class CommandDelayRunnable : Runnable {

        override fun run() {
            synchronized(mOutputCommandQueue) {
                val commandContext = mOutputCommandQueue.peek()
                processCommand(commandContext)
            }
        }
    }

    private inner class ConnectTimeoutRunnable : Runnable {
        override fun run() {
        }
    }

    companion object {
        val CONNECTION_PRIORITY_BALANCED = 0
        val CONNECTION_PRIORITY_HIGH = 1
        val CONNECTION_PRIORITY_LOW_POWER = 2
        private val CONN_STATE_IDLE = 1
        private val CONN_STATE_CONNECTING = 2
        private val CONN_STATE_CONNECTED = 4
        private val CONN_STATE_DISCONNECTING = 8
        private val CONN_STATE_CLOSED = 16
        private val RSSI_UPDATE_TIME_INTERVAL = 2000
    }
}
