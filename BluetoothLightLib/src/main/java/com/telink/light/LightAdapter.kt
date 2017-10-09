package com.telink.light

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.RequiresApi
import android.text.TextUtils
import com.telink.event.Event
import com.telink.event.EventListener
import com.telink.mode.DeviceInfo
import com.telink.mode.OtaDeviceInfo
import com.telink.param.Parameters
import com.telink.util.Arrays
import com.telink.util.BleLog
import com.telink.util.Strings
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LightAdapter {

    private val mConnectionListener = ConnectionListener()
    private val mResetMeshListener = ResetMeshListener()
    private val mOtaListener = OtaListener()
    private val mFirmwareListener = GetFirmwareListener()
    private val mGetLtkListener = GetLongTermKeyListener()
    private val mNotificationListener = NotificationListener()
    private val mDeleteListener = DeleteListener()
    private val mCommandListener = NormalCommandListener()

    private val mode = AtomicInteger(MODE_IDLE)
    private val state = AtomicInteger(0)
    private val status = AtomicInteger(-1)
    private val isStarted = AtomicBoolean(false)
    private val updateCount = AtomicInteger(0)
    private val isScanStopped = AtomicBoolean(true)
    private val nextLightIndex = AtomicInteger(0)

    private lateinit var mCallback: Callback
    private lateinit var mContext: Context
    private lateinit var parameters: Parameters
    private lateinit var mLightCtrl: LightController
    private lateinit var mScannedLights: LightPeripherals
    private lateinit var mUpdateLights: LightPeripherals
    private lateinit var mLoopHandler: Handler
    private lateinit var mLoopTask: Runnable
    private val mInterval = 200

    private lateinit var mNotifyHandler: Handler
    private lateinit var mNotifyTask: Runnable
    private var lightCount = 0

    private lateinit var mScanCallback: ScanCallback

    private var mainLoopRunning = false
    private var autoRefreshRunning = false
    private lateinit var autoRefreshParams: Parameters
    private var autoRefreshCount: Int = 0

    private var lastLogoutTime: Long = 0
    private var lastScanTime: Long = 0

    // 蓝牙扫描开启时间
    private var scanStartTime: Long = 0
    // 扫描延时
    private lateinit var mScanDelayHandler: Handler

    private lateinit var mThread: HandlerThread

    val isLogin = mLightCtrl.isLogin

    val firmwareVersion: Boolean
        get() {
            if (!isStarted.get())
                return false
            val light = mLightCtrl.currentLight
            if (light == null || !light.isConnected)
                return false
            BleLog.d("LightAdapter#getFirmwareVersion")
            mLightCtrl.requestFirmware()
            return true
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val stopScanTask = Runnable {
        if (!isScanStopped.get()) {
            LeBluetooth.instance!!.stopScan()
        }
    }

    private val conFail = 0

    private val isSupportN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private val isN = Build.VERSION.SDK_INT == Build.VERSION_CODES.N

    fun getMode() = mode.get()

    @Synchronized private fun setMode(value: Int) {
        mode.getAndSet(value)
        if (mLightCtrl != null) {
            mLightCtrl.setIsUpdating(value == MODE_UPDATE_MESH)
        }
        BleLog.d("set mode : " + value)
    }

    private fun getModeStr(value: Int) = when (value) {
        MODE_IDLE -> "idle"
        MODE_SCAN_MESH -> "scan"
        MODE_UPDATE_MESH -> "update"
        MODE_AUTO_CONNECT_MESH -> "auto connect"
        else -> "null"
    }


    @Synchronized
    fun start(context: Context) {
        BleLog.d("light mAdapter start")
        if (isStarted.get())
            return
        setIsStarted(true)
        setMode(MODE_IDLE)
        mContext = context

        mScannedLights = LightPeripherals()
        mUpdateLights = LightPeripherals()
        mScanCallback = ScanCallback()

        mLightCtrl = LightController()
        mLightCtrl.addEventListener(LightController.LightEvent.NOTIFICATION_RECEIVE, mNotificationListener)
        mLightCtrl.addEventListener(LightController.LightEvent.CONNECT_SUCCESS, mConnectionListener)
        mLightCtrl.addEventListener(LightController.LightEvent.CONNECT_FAILURE, mConnectionListener)
        mLightCtrl.addEventListener(LightController.LightEvent.CONNECT_FAILURE_N, mConnectionListener)// android N
        mLightCtrl.addEventListener(LightController.LightEvent.LOGIN_SUCCESS, mConnectionListener)
        mLightCtrl.addEventListener(LightController.LightEvent.LOGIN_FAILURE, mConnectionListener)
        mLightCtrl.addEventListener(LightController.LightEvent.RESET_MESH_SUCCESS, mResetMeshListener)
        mLightCtrl.addEventListener(LightController.LightEvent.RESET_MESH_FAILURE, mResetMeshListener)
        mLightCtrl.addEventListener(LightController.LightEvent.OTA_SUCCESS, mOtaListener)
        mLightCtrl.addEventListener(LightController.LightEvent.OTA_PROGRESS, mOtaListener)
        mLightCtrl.addEventListener(LightController.LightEvent.OTA_FAILURE, mOtaListener)
        mLightCtrl.addEventListener(LightController.LightEvent.GET_FIRMWARE_SUCCESS, mFirmwareListener)
        mLightCtrl.addEventListener(LightController.LightEvent.GET_FIRMWARE_FAILURE, mFirmwareListener)
        mLightCtrl.addEventListener(LightController.LightEvent.GET_LTK_SUCCESS, mGetLtkListener)
        mLightCtrl.addEventListener(LightController.LightEvent.GET_LTK_FAILURE, mGetLtkListener)
        mLightCtrl.addEventListener(LightController.LightEvent.DELETE_SUCCESS, mDeleteListener)
        mLightCtrl.addEventListener(LightController.LightEvent.DELETE_FAILURE, mDeleteListener)
        //mLightCtrl.addEventListener(LightController.LightEvent.COMMAND_SUCCESS, mCommandListener);
        //mLightCtrl.addEventListener(LightController.LightEvent.COMMAND_FAILURE, mCommandListener);

        mThread = HandlerThread("LightAdapter Thread")
        mThread.start()
        mLoopHandler = Handler(mThread.looper)
        mLoopTask = EventLoopTask()

        mNotifyHandler = Handler(mThread.looper)
        mNotifyTask = RefreshNotifyTask()
        enableLoop(true)
        mScanDelayHandler = Handler()
        LeBluetooth.instance!!.setLeScanCallback(mScanCallback)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Synchronized
    fun stop() {
        BleLog.d("light mAdapter stop")
        if (!isStarted.get())
            return
        setIsStarted(false)
        setMode(MODE_IDLE)
        stopLeScan()
        enableLoop(false)
        enableRefreshNotify(false)

        if (mLoopHandler != null) {
            mLoopHandler.removeCallbacksAndMessages(null)
        }
        mThread.quit()
        if (mNotifyHandler != null) {
            mNotifyHandler.removeCallbacksAndMessages(null)
        }
        if (mScanDelayHandler != null) {
            mScanDelayHandler.removeCallbacksAndMessages(null)
        }

        mLightCtrl.removeEventListeners()
        mLightCtrl.disconnect()
        mScannedLights.clear()
        mUpdateLights.clear()
    }

    fun connect(mac: String, timeoutSeconds: Int): Boolean {

        if (!isStarted.get())
            return false

        val index = mScannedLights.getPeripheralIndex(mac)

        if (index == -1)
            return false

        BleLog.d("LightAdapter#connect")
        val light = mScannedLights[mac]
        connect(light, timeoutSeconds)
        return true
    }

    fun disconnect() {

        if (!isStarted.get())
            return
        BleLog.d("LightAdapter#disconnect")
        mLightCtrl.disconnect()
    }

    private fun connect(light: LightPeripheral?, timeoutSeconds: Int) {

        val currentLight = mLightCtrl.currentLight

        if (currentLight != null && currentLight.isConnected)
            mLightCtrl.disconnect()

        mLightCtrl.setTimeoutSeconds(timeoutSeconds)
        if (light != null) {
            mLightCtrl.connect(mContext, light)
        }
        setStatus(STATUS_CONNECTING)
    }

    fun login(meshName: ByteArray, password: ByteArray): Boolean {

        if (!isStarted.get())
            return false

        val light = mLightCtrl.currentLight
        if (light == null || !light.isConnected)
            return false
        BleLog.d("LightAdapter#login")
        mLightCtrl.login(meshName, password)
        return true
    }

    fun startOta(firmware: ByteArray): Boolean {
        if (!isStarted.get())
            return false
        val light = mLightCtrl.currentLight
        if (light == null || !mLightCtrl.isLogin)
            return false
        BleLog.d("LightAdapter#startOta")
        mLightCtrl.startOta(firmware)
        return true
    }


    private fun login(light: LightPeripheral) {

        val meshName = java.util.Arrays.copyOf(light.getMeshName(), 16)
        val pwd = parameters.getString(Parameters.PARAM_MESH_PASSWORD) ?: return
        val password = Strings.stringToBytes(pwd, 16)
        login(meshName, password)
    }

    fun sendCommand(opcode: Byte, address: Int, params: ByteArray) = sendCommand(opcode, address, params, null, 0)

    fun sendCommand(opcode: Byte, address: Int, params: ByteArray, tag: Any) = sendCommand(opcode, address, params, tag, 0)

    fun sendCommand(opcode: Byte, address: Int, params: ByteArray, delay: Int) = sendCommand(opcode, address, params, null, delay)

    private fun sendCommand(opcode: Byte, address: Int, params: ByteArray, tag: Any?, delay: Int): Boolean {

        if (!isStarted.get())
            return false

        if (!mLightCtrl.isLogin)
            return false

        return if (tag == null)
            mLightCtrl.sendCommand(opcode, address, params, false, delay)
        else
            mLightCtrl.sendCommand(opcode, address, params, false, tag, delay)
    }

    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray): Boolean {
        return sendCommandNoResponse(opcode, address, params, null, 0)
    }

    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray, tag: Any): Boolean {
        return sendCommandNoResponse(opcode, address, params, tag, 0)
    }

    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray, delay: Int): Boolean {
        return sendCommandNoResponse(opcode, address, params, null, delay)
    }

    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray, tag: Any?, delay: Int): Boolean {

        if (!isStarted.get())
            return false

        if (!mLightCtrl.isLogin)
            return false

        return if (tag == null)
            mLightCtrl.sendCommand(opcode, address, params, true, delay)
        else
            mLightCtrl.sendCommand(opcode, address, params, true, tag, delay)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Synchronized
    fun startScan(params: Parameters, callback: Callback) {

        if (!isStarted.get())
            return

        if (getMode() == MODE_SCAN_MESH)
            return
        BleLog.d("LightAdapter#startLeScan")
        setMode(MODE_IDLE)

        if (!isSupportN)
            LeBluetooth.instance!!.stopScan()

        parameters = params
        mCallback = callback
        mUpdateLights.clear()
        mScannedLights.clear()
        mLightCtrl.disconnect()

        lastScanTime = System.currentTimeMillis()
        setMode(MODE_SCAN_MESH)
        enableLoop(true)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Synchronized
    fun updateMesh(params: Parameters, callback: Callback) {

        if (!isStarted.get())
            return

        if (getMode() == MODE_UPDATE_MESH)
            return
        BleLog.d("LightAdapter#updateMesh")
        setMode(MODE_IDLE)

        if (!isSupportN)
            LeBluetooth.instance!!.stopScan()

        parameters = params
        mCallback = callback

        val updateObj = parameters[Parameters.PARAM_DEVICE_LIST]
        mUpdateLights.clear()

        if (updateObj != null) {

            if (updateObj is DeviceInfo) {
                val deviceInfo = updateObj as DeviceInfo?
                val peripheral = mScannedLights[deviceInfo!!.macAddress]
                if (peripheral != null) {
                    peripheral.newMeshAddress = deviceInfo.meshAddress
                    mUpdateLights.put(peripheral)
                }
            } else if (updateObj is Iterable<*>) {
                val iterable = updateObj as Iterable<DeviceInfo>?
                val iterator = iterable!!.iterator()

                var deviceInfo: DeviceInfo?

                while (iterator.hasNext()) {

                    deviceInfo = iterator.next()

                    if (deviceInfo != null) {
                        val peripheral = mScannedLights!![deviceInfo.macAddress]

                        if (peripheral != null) {
                            peripheral.newMeshAddress = deviceInfo.meshAddress
                            mUpdateLights.put(peripheral)
                        }
                    }
                }
            }

        } else {
            mScannedLights.copyTo(mUpdateLights)
        }

        nextLightIndex.set(0)
        updateCount.set(0)
        lightCount = mUpdateLights.size()
        mLightCtrl.disconnect()

        setMode(MODE_UPDATE_MESH)
        setState(STATE_RUNNING)

        enableLoop(true)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Synchronized
    fun autoConnect(params: Parameters, callback: Callback) {

        if (!isStarted.get())
            return

        if (getMode() == MODE_AUTO_CONNECT_MESH)
            return
        BleLog.d("LightAdapter#autoConnect")
        setMode(MODE_IDLE)

        if (!isSupportN)
            LeBluetooth.instance!!.stopScan()

        parameters = params
        mCallback = callback
        mScannedLights.clear()
        mUpdateLights.clear()
        mLightCtrl.disconnect()
        lightCount = 0
        updateCount.set(0)
        nextLightIndex.set(0)

        lastLogoutTime = 0

        setMode(MODE_AUTO_CONNECT_MESH)
        setState(STATE_RUNNING)

        enableLoop(true)
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun stopLeScan() {
        val delay = if (isSupportN && System.currentTimeMillis() - scanStartTime < MIN_SCAN_PERIOD) {
            MIN_SCAN_PERIOD - (System.currentTimeMillis() - scanStartTime)
        } else {
            0
        }
        mScanDelayHandler.postDelayed(stopScanTask, delay)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Synchronized
    fun idleMode(disconnect: Boolean) {

        if (!isStarted.get())
            return
        if (getMode() == MODE_IDLE)
            return
        setMode(MODE_IDLE)
        status.getAndSet(-1)
        enableLoop(false)

        if (disconnect) {
            mLightCtrl.disconnect()
        }

        stopLeScan()
    }

    @Synchronized
    fun startOta(params: Parameters, callback: Callback) {

        if (!isStarted.get())
            return

        if (getMode() == MODE_OTA)
            return
        BleLog.d("LightAdapter#startOta")
        setMode(MODE_IDLE)

        parameters = params
        mCallback = callback
        mUpdateLights.clear()
        lightCount = 0
        updateCount.set(0)
        nextLightIndex.set(0)

        setMode(MODE_OTA)
        setState(STATE_RUNNING)
        enableLoop(true)
    }


    fun enableAutoRefreshNotify(params: Parameters) {

        if (!isStarted.get())
            return
        BleLog.d("LightAdapter#enableAutoRefreshNotify")
        autoRefreshParams = params
        autoRefreshCount = 0
        enableRefreshNotify(true)
    }

    fun disableAutoRefreshNotify() {

        if (!isStarted.get())
            return
        BleLog.d("LightAdapter#disableAutoRefreshNotify")
        enableRefreshNotify(false)
    }

    fun enableNotification() {
        if (!isStarted.get())
            return
        BleLog.d("LightAdapter#enableNotification")
        mLightCtrl.enableNotification()
    }

    fun disableNotification() {
        if (!isStarted.get())
            return
        BleLog.d("LightAdapter#disableNotification")
        mLightCtrl.disableNotification()
    }

    fun updateNotification() {
        if (!isStarted.get())
            return
        BleLog.d("LightAdapter#updateNotification")
        mLightCtrl.updateNotification()
    }

    fun updateNotification(params: ByteArray) {
        if (!isStarted.get())
            return
        BleLog.d("LightAdapter#updateNotification-with-params")
        mLightCtrl.updateNotification(params)
    }

    private fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?): LightPeripheral? {
        val filterChain = AdvertiseFilterChain.default
        val iterator = filterChain.iterator()
        var filter: AdvertiseDataFilter<*>
        var light: LightPeripheral? = null

        while (iterator.hasNext()) {
            filter = iterator.next()
            try {
                light = filter.filter(device, rssi, scanRecord!!)
            } catch (e: Exception) {
                BleLog.d("Advertise Filter Exception : " + filter.toString() + "--" + e.message)
            }

            if (light != null)
                break
        }

        return light
    }

    private fun onLeScanFilter(light: LightPeripheral): Boolean {

        val mode = getMode()

        val params = parameters

        val outOfMeshName: ByteArray

        val meshName = Strings.stringToBytes(params.getString(Parameters.PARAM_MESH_NAME)!!, 16)
        val meshName1 = light.getMeshName()

        if (mode == MODE_SCAN_MESH) {
            outOfMeshName = Strings.stringToBytes(params.getString(Parameters.PARAM_OUT_OF_MESH)!!, 16)
            if (!Arrays.equals(meshName, meshName1) && !Arrays.equals(outOfMeshName, meshName1))
                return false
        } else if (mode == MODE_AUTO_CONNECT_MESH) {
            if (!Arrays.equals(meshName, meshName1))
                return false
            val mac = params.getString(Parameters.PARAM_AUTO_CONNECT_MAC)
            return TextUtils.isEmpty(mac) || mac == light.macAddress
        }

        return true
    }

    private fun onNotification(data: ByteArray) {

        val length = data.size
        val minLength = 20
        var position = 7

        if (length < minLength)
            return

        val opcode = data[position++].toInt() and 0xFF
        val vendorId = (data[position++].toInt() shl 8) + data[position]

        if (vendorId != Manufacture.default.vendorId)
            return

        val src = data[3] + (data[4].toInt() shl 8)
        val params = ByteArray(10)

        System.arraycopy(data, 10, params, 0, 10)

        if (mCallback != null)
            mCallback.onNotify(mLightCtrl.currentLight, getMode(),opcode, src, params)
    }



    private fun getState() = state.get()

    @Synchronized private fun setState(value: Int) {
        state.getAndSet(value)
    }

    @Synchronized private fun setIsStarted(value: Boolean) {
        isStarted.getAndSet(value)
    }

    @Synchronized private fun setStatus(newStatus: Int, ignoreIdleMode: Boolean, ignoreStatus: Boolean) {

        if (!ignoreIdleMode) {
            if (getMode() == MODE_IDLE)
                return
        }

        if (!ignoreStatus) {
            if (status.get() == newStatus)
                return
        }

        val oldStatus = status.getAndSet(newStatus)

        if (mCallback != null)
            mCallback.onStatusChanged(mLightCtrl,getMode(), oldStatus, newStatus)
    }

    private fun setStatus(newStatus: Int, ignoreIdleMode: Boolean) {
        setStatus(newStatus, ignoreIdleMode, false)
    }

    private fun setStatus(newStatus: Int) {
        setStatus(newStatus, false, false)
    }

    private fun enableLoop(running: Boolean) {

        if (mLoopHandler == null || mLoopTask == null)
            return

        if (running) {
            if (!mainLoopRunning) {
                mLoopHandler.postDelayed(mLoopTask,mInterval.toLong())
            }

        } else {
            mLoopHandler.removeCallbacks(mLoopTask)
        }

        mainLoopRunning = running
    }

    private fun enableRefreshNotify(enable: Boolean) {

        if (mNotifyHandler == null || mNotifyTask == null)
            return

        if (enable) {
            if (autoRefreshRunning)
                return

            autoRefreshCount = 0
            autoRefreshRunning = true
            mNotifyHandler.postDelayed(mNotifyTask, 0)
        } else {
            mNotifyHandler.removeCallbacks(mNotifyTask)
            autoRefreshRunning = false
        }
    }

    interface Callback {

        fun onLeScan(light: LightPeripheral, mode: Int, scanRecord: ByteArray?): Boolean

        fun onStatusChanged(controller: LightController?, mode: Int, oldStatus: Int, newStatus: Int)

        fun onNotify(light: LightPeripheral, mode: Int, opcode: Int, src: Int, params: ByteArray)

        fun onCommandResponse(light: LightPeripheral, mode: Int, command: Command, success: Boolean)

        fun onError(errorCode: Int)
    }

    private inner class ScanCallback : LeBluetooth.LeScanCallback {

        override fun onScanFail(errorCode: Int) {
            BleLog.d(" scan fail : " + errorCode)
            if (mCallback != null)
                mCallback.onError(errorCode)
        }

        override fun onStartedScan() {
            scanStartTime = System.currentTimeMillis()
            isScanStopped.set(false)
        }

        override fun onStoppedScan() {
            isScanStopped.set(true)
        }

        override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?) {

            if (mCallback == null || getMode() == MODE_IDLE || getMode() == MODE_UPDATE_MESH)
                return

            synchronized(this@LightAdapter) {
                if (mScannedLights.contains(device.address))
                    return
            }

            val light = this@LightAdapter.onLeScan(device, rssi, scanRecord) ?: return

            // Device Name 有可能与 广播包中的MeshName不一致
            val result = onLeScanFilter(light)
            if (!result)
                return
            BleLog.d("add scan result : " + device.address)

            val mode = getMode()

            if (mode == MODE_SCAN_MESH) {

                val isSingleScan = parameters.getBoolean(Parameters.PARAM_SCAN_TYPE_SINGLE, false)

                if (isSingleScan) {

                    if (mScannedLights.size() == 0) {
                        mScannedLights.put(light)
                        mCallback.onLeScan(light, mode, scanRecord)
                    }

                } else {
                    mScannedLights.put(light)
                    mCallback.onLeScan(light, mode, scanRecord)
                }

            } else if (mode == MODE_AUTO_CONNECT_MESH) {
                mScannedLights.put(light)
            } else if (mode == MODE_OTA) {
                mScannedLights.put(light)
            }
        }

    }

    private inner class ConnectionListener : EventListener<Int> {

        private fun onConnected() {

            setStatus(STATUS_CONNECTED, true)

            val mode = getMode()

            if (mode != MODE_IDLE) {

                if (mode == MODE_UPDATE_MESH) {
                    mLightCtrl.requestFirmware()
                }

                setStatus(STATUS_LOGINING)

                val light = mLightCtrl.currentLight
                if (light != null) {
                    login(light)
                }
            } else {
                mLightCtrl.requestFirmware()
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun onLoginSuccess() {

            BleLog.d("onLoginSuccess " + mLightCtrl.currentLight.macAddress)

            setStatus(STATUS_LOGIN, true)

            val mode = getMode()

            if (mode == MODE_UPDATE_MESH) {

                setStatus(STATUS_UPDATING_MESH)

                val meshName = Strings.stringToBytes(parameters.getString(Parameters.PARAM_NEW_MESH_NAME)!!, 16)
                val password = Strings.stringToBytes(parameters.getString(Parameters.PARAM_NEW_PASSWORD)!!, 16)
                val ltk = parameters.getBytes(Parameters.PARAM_LONG_TERM_KEY)

                mLightCtrl.reset(meshName, password, ltk)

            } else if (mode == MODE_AUTO_CONNECT_MESH) {
                enableLoop(false)
                setState(STATE_PENDING)
                stopLeScan()
                mScannedLights.clear()
                nextLightIndex.set(0)
                lastLogoutTime = 0
                val enable = parameters.getBoolean(Parameters.PARAM_AUTO_ENABLE_NOTIFICATION)
                if (enable) {
                    mLightCtrl.enableNotification()
                    if (autoRefreshParams != null) {
                        autoRefreshRunning = false
                        enableRefreshNotify(true)
                    } else {
                        mLightCtrl.updateNotification()
                    }
                }

            } else if (mode == MODE_OTA) {
                setState(STATE_PENDING)
                val otaDeviceInfo = parameters[Parameters.PARAM_DEVICE_LIST] as OtaDeviceInfo?
                mLightCtrl.startOta(otaDeviceInfo!!.firmware!!)
            }
        }

        private fun onLoginFailure() {

            BleLog.d("onLoginFail " + mLightCtrl.currentLight.macAddress)

            setStatus(STATUS_LOGOUT, true)

            val mode = getMode()

            when (mode) {
                MODE_UPDATE_MESH -> {
                    setState(STATE_RUNNING)
                    setStatus(STATUS_UPDATE_MESH_FAILURE)
                }
                MODE_AUTO_CONNECT_MESH -> {
                    mScannedLights.removeTop()
                    nextLightIndex.set(0)
                    setState(STATE_RUNNING)
                    enableLoop(true)
                }
                MODE_OTA -> {
                    setState(STATE_PENDING)
                    setStatus(STATUS_OTA_FAILURE)
                }
            }
        }

        private fun onNError() {
            BleLog.d("onNError " + mLightCtrl.currentLight.macAddress)

            setStatus(STATUS_LOGOUT, true)

            val mode = getMode()
            if (mode == MODE_UPDATE_MESH) {
                setState(STATE_RUNNING)
                setStatus(STATUS_ERROR_N)
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun performed(event: Event<Int>) {
            when (event.type) {
                LightController.LightEvent.CONNECT_SUCCESS -> onConnected()
                LightController.LightEvent.LOGIN_SUCCESS -> onLoginSuccess()
                LightController.LightEvent.LOGIN_FAILURE, LightController.LightEvent.CONNECT_FAILURE -> onLoginFailure()
                LightController.LightEvent.CONNECT_FAILURE_N -> onNError()
            }
        }
    }

    private inner class NormalCommandListener : EventListener<Int> {

        private fun onCommandSuccess(command: Command) {

            if (mCallback != null)
                mCallback.onCommandResponse(mLightCtrl.currentLight, getMode(), command, true)
        }

        private fun onCommandFailure(command: Command) {

            if (mCallback != null)
                mCallback.onCommandResponse(mLightCtrl.currentLight, getMode(), command, false)
        }

        override fun performed(event: Event<Int>) {
            val lightEvent = event as LightController.LightEvent
            when (lightEvent.type) {
                LightController.LightEvent.COMMAND_SUCCESS -> onCommandSuccess(lightEvent.args as Command)
                LightController.LightEvent.COMMAND_FAILURE -> onCommandFailure(lightEvent.args as Command)
            }
        }
    }

    private inner class NotificationListener : EventListener<Int> {

        override fun performed(event: Event<Int>) {
            if (mCallback != null) {
                val lightEvent = event as LightController.LightEvent
                this@LightAdapter.onNotification(lightEvent.args as ByteArray)
            }
        }
    }

    private inner class ResetMeshListener : EventListener<Int> {

        private fun onResetMeshSuccess() {

            BleLog.d("onResetMeshSuccess " + mLightCtrl.currentLight.macAddress)

            setStatus(STATUS_UPDATE_MESH_COMPLETED)

            if (getMode() == MODE_UPDATE_MESH) {
                updateCount.getAndIncrement()
                setState(STATE_RUNNING)
            }
        }

        private fun onResetMeshFailure(reason: String) {

            BleLog.d("onResetMeshFail " + mLightCtrl.currentLight.macAddress+ " error msg : " + reason)

            setStatus(STATUS_UPDATE_MESH_FAILURE)

            if (getMode() == MODE_UPDATE_MESH) {
                setState(STATE_RUNNING)
            }
        }

        override fun performed(event: Event<Int>) {
            val lightEvent = event as LightController.LightEvent
            when (lightEvent.type) {
                LightController.LightEvent.RESET_MESH_SUCCESS -> onResetMeshSuccess()
                LightController.LightEvent.RESET_MESH_FAILURE -> onResetMeshFailure(lightEvent.args as String)
            }
        }
    }

    private inner class GetFirmwareListener : EventListener<Int> {

        private fun onGetFirmwareSuccess() {
            val mode = getMode()
            if (mode == MODE_UPDATE_MESH || mode == MODE_AUTO_CONNECT_MESH || mode == MODE_OTA)
                return
            setStatus(STATUS_GET_FIRMWARE_COMPLETED, true)
        }

        private fun onGetFirmwareFailure() {
            val mode = getMode()
            if (mode == MODE_UPDATE_MESH || mode == MODE_AUTO_CONNECT_MESH || mode == MODE_OTA)
                return
            setStatus(STATUS_GET_FIRMWARE_FAILURE, true)
        }

        override fun performed(event: Event<Int>) {
            when (event.type) {
                LightController.LightEvent.GET_FIRMWARE_SUCCESS -> onGetFirmwareSuccess()
                LightController.LightEvent.GET_FIRMWARE_FAILURE -> onGetFirmwareFailure()
            }
        }
    }

    private inner class GetLongTermKeyListener : EventListener<Int> {

        override fun performed(event: Event<Int>) {
            when (event.type) {
                LightController.LightEvent.GET_LTK_SUCCESS -> setStatus(STATUS_GET_LTK_COMPLETED)
                LightController.LightEvent.GET_LTK_FAILURE -> setStatus(STATUS_GET_LTK_FAILURE)
            }
        }
    }

    private inner class DeleteListener : EventListener<Int> {

        override fun performed(event: Event<Int>) {
            when (event.type) {
                LightController.LightEvent.DELETE_SUCCESS -> {
                    setStatus(STATUS_DELETE_COMPLETED)
                    setMode(MODE_IDLE)
                }
                LightController.LightEvent.DELETE_FAILURE -> {
                    setStatus(STATUS_DELETE_FAILURE)
                    setMode(MODE_IDLE)
                }
            }
        }
    }

    private inner class OtaListener : EventListener<Int> {

        private fun onOtaSuccess() {
            BleLog.d("OTA Success")
            setStatus(STATUS_OTA_COMPLETED, true)
            setMode(MODE_IDLE)
        }

        private fun onOtaFailure() {
            BleLog.d("OTA Failure")
            setStatus(STATUS_OTA_FAILURE, true)
            setMode(MODE_IDLE)
        }

        override fun performed(event: Event<Int>) {
            when (event.type) {
                LightController.LightEvent.OTA_PROGRESS -> setStatus(STATUS_OTA_PROGRESS, true, true)
                LightController.LightEvent.OTA_SUCCESS -> onOtaSuccess()
                LightController.LightEvent.OTA_FAILURE -> onOtaFailure()
            }
        }
    }


    private inner class EventLoopTask : Runnable {

        private var pause: Boolean = false
        private var lastUpdateTime: Long = 0
        private val waitSeconds = 5 * 1000

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun run() {

            val mode = getMode()

            when (mode) {
                MODE_SCAN_MESH -> leScan()
                MODE_UPDATE_MESH -> update()
                MODE_AUTO_CONNECT_MESH -> autoConnect()
                MODE_OTA -> autoOta()
            }

            if (mLoopHandler != null)
                mLoopHandler.postDelayed(this, mInterval.toLong())
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun leScan() {

            if (!startLeScan()) {
                setMode(MODE_IDLE)
                return
            }

            val isSingleScan = parameters.getBoolean(Parameters.PARAM_SCAN_TYPE_SINGLE, false)

            if (isSingleScan) {

                if (mScannedLights.size() == 1) {
                    setStatus(STATUS_MESH_SCAN_COMPLETED)
                    idleMode(false)
                    return
                }
            }

            var timeoutSeconds = parameters.getInt(Parameters.PARAM_SCAN_TIMEOUT_SECONDS, 0)

            if (timeoutSeconds <= 0)
                return

            val currentTime = System.currentTimeMillis()
            timeoutSeconds *= 1000

            if (currentTime - lastScanTime >= timeoutSeconds) {

                BleLog.d("scan timeout")

                if (isSingleScan) {
                    setStatus(STATUS_MESH_SCAN_TIMEOUT)
                }

                setStatus(STATUS_MESH_SCAN_COMPLETED)
                idleMode(false)
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun update() {

            if (getState() == STATE_PENDING)
                return

            if (updateCount.get() >= lightCount || nextLightIndex.get() >= lightCount) {
                setState(STATE_PENDING)
                nextLightIndex.set(0)
                setStatus(STATUS_UPDATE_ALL_MESH_COMPLETED)
                idleMode(false)
                return
            }

            setState(STATE_PENDING)

            val light = mUpdateLights[nextLightIndex.getAndIncrement()]

            if (light == null || light.meshChanged) {
                setState(STATE_RUNNING)
                return
            }

            val timeoutSeconds = parameters.getInt(Parameters.PARAM_TIMEOUT_SECONDS)
            connect(light, timeoutSeconds)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun autoConnect() {

            if (getState() == STATE_PENDING)
                return

            if (pause) {
                val currentTime = System.currentTimeMillis()
                val delay = currentTime - lastUpdateTime

                if (delay < waitSeconds)
                    return
                else
                    pause = false
            }

            if (!startLeScan()) {
                setMode(MODE_IDLE)
                return
            }

            if (checkOffLine()) {
                return
            }

            val count = mScannedLights.size()

            if (count <= 0) {
                return
            }
            setState(STATE_PENDING)
            val timeoutSeconds = parameters.getInt(Parameters.PARAM_TIMEOUT_SECONDS)
            val light = mScannedLights.top
            if (light != null) {
                connect(light, timeoutSeconds)
            } else {
                setState(STATE_RUNNING)
            }
        }

        private fun autoOta() {

            if (getState() == STATE_PENDING)
                return

            val count = mScannedLights.size()
            if (count <= 0)
                return

            setState(STATE_PENDING)
            val timeoutSeconds = parameters.getInt(Parameters.PARAM_TIMEOUT_SECONDS)
            val deviceInfo = parameters[Parameters.PARAM_DEVICE_LIST] as OtaDeviceInfo?
            val light = mScannedLights[deviceInfo!!.macAddress]

            if (light == null) {
                setStatus(STATUS_OTA_FAILURE)
                setMode(MODE_IDLE)
                return
            }

            if (light.isConnected) {
                BleLog.d("login")
                login(light)
            } else {
                BleLog.d("connect")
                connect(light, timeoutSeconds)
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun startLeScan(): Boolean {
            mScanDelayHandler.removeCallbacks(stopScanTask)
            if (!LeBluetooth.instance!!.isScanning) {
                if (!LeBluetooth.instance!!.startScan(null))
                    return false
                lastLogoutTime = 0
            }

            return true
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun checkOffLine(): Boolean {

            if (lastLogoutTime == 0L) {
                lastLogoutTime = System.currentTimeMillis() - mInterval
                return false
            }

            var checkOffLineTime = parameters.getInt(Parameters.PARAM_OFFLINE_TIMEOUT_SECONDS, 0) * 1000

            if (checkOffLineTime <= 0)
                checkOffLineTime = CHECK_OFFLINE_TIME

            val currentTime = System.currentTimeMillis()

            return if (currentTime - lastLogoutTime > checkOffLineTime) {
                lastLogoutTime = 0
                stopScan()
                setStatus(STATUS_MESH_OFFLINE)
                true
            } else {
                false
            }
        }


        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun stopScan() {
            stopLeScan()
            pause = true
            lastUpdateTime = System.currentTimeMillis()
        }
    }

    private inner class RefreshNotifyTask : Runnable {

        override fun run() {

            if (getMode() != MODE_AUTO_CONNECT_MESH)
                return

            if (autoRefreshParams == null)
                return
            if (!mLightCtrl.isLogin)
                return

            var delay = autoRefreshParams.getInt(Parameters.PARAM_AUTO_REFRESH_NOTIFICATION_DELAY,
                    AUTO_REFRESH_NOTIFICATION_DELAY)

            if (delay <= 0)
                delay = AUTO_REFRESH_NOTIFICATION_DELAY

            mLightCtrl.updateNotification()
            val repeat = autoRefreshParams.getInt(Parameters.PARAM_AUTO_REFRESH_NOTIFICATION_REPEAT, 1)

            if (repeat > 0) {
                val count = autoRefreshCount + 1

                if (count > repeat) {
                    autoRefreshRunning = false
                } else {
                    autoRefreshCount = count
                    BleLog.d("AutoRefresh : " + count)
                    mNotifyHandler.postDelayed(this, delay.toLong())
                }
            } else if (repeat <= 0) {
                mNotifyHandler.postDelayed(this, delay.toLong())
            }
        }
    }


    private inner class LightPeripherals {

        private val mPeripherals: MutableList<LightPeripheral>

        val top: LightPeripheral?
            get() = if (mPeripherals.size <= 0) {
                null
            } else mPeripherals[0]

        val byMaxRssi: LightPeripheral?
            get() {
                var result: LightPeripheral? = null

                synchronized(this) {
                    mPeripherals
                            .asSequence()
                            .filter { result == null || it.rssi > result!!.rssi }
                            .forEach { result = it }
                }

                return result
            }

        init {
            mPeripherals = ArrayList()
        }

        fun put(light: LightPeripheral) {

            val index = getPeripheralIndex(light.macAddress)

            if (index == -1)
                mPeripherals.add(light)
        }

        operator fun get(index: Int): LightPeripheral? {

            return if (index >= 0 && index < mPeripherals.size) mPeripherals[index] else null

        }

        fun removeTop(): Boolean {
            if (mPeripherals.size <= 0) {
                return false
            }
            if (mPeripherals[0].retry == 0) {
                mPeripherals[0].addRetry()
            } else {
                mPeripherals.removeAt(0)
            }

            return true
        }

        operator fun get(macAddress: String): LightPeripheral? {

            val index = getPeripheralIndex(macAddress)

            return if (index != -1) {
                this[index]
            } else null

        }

        operator fun contains(macAddress: String): Boolean {

            val index = getPeripheralIndex(macAddress)

            return index != -1
        }

        fun size(): Int {
            synchronized(this) {
                return mPeripherals.size
            }
        }

        fun clear() {
            synchronized(this) {
                mPeripherals.clear()
            }
        }

        fun getPeripheralIndex(macAddress: String): Int {

            val count = size()

            var peripheral: Peripheral

            for (i in 0 until count) {

                peripheral = mPeripherals[i]

                if (peripheral.macAddress == macAddress)
                    return i
            }
            return -1
        }

        fun copyTo(dest: LightPeripherals) {
            for (light in mPeripherals) {
                dest.put(light)
            }
        }
    }

    companion object {

        val STATUS_CONNECTING = 0
        val STATUS_CONNECTED = 1
        val STATUS_LOGINING = 2
        val STATUS_LOGIN = 3
        val STATUS_LOGOUT = 4
        val STATUS_ERROR_N = 5 // android N
        val STATUS_UPDATE_MESH_COMPLETED = 10
        val STATUS_UPDATING_MESH = 11
        val STATUS_UPDATE_MESH_FAILURE = 12
        val STATUS_UPDATE_ALL_MESH_COMPLETED = 13
        val STATUS_GET_LTK_COMPLETED = 20
        val STATUS_GET_LTK_FAILURE = 21
        val STATUS_MESH_OFFLINE = 30
        val STATUS_MESH_SCAN_COMPLETED = 40
        val STATUS_MESH_SCAN_TIMEOUT = 41
        val STATUS_OTA_COMPLETED = 50
        val STATUS_OTA_FAILURE = 51
        val STATUS_OTA_PROGRESS = 52
        val STATUS_GET_FIRMWARE_COMPLETED = 60
        val STATUS_GET_FIRMWARE_FAILURE = 61
        val STATUS_DELETE_COMPLETED = 70
        val STATUS_DELETE_FAILURE = 71

        val MODE_IDLE = 1
        val MODE_SCAN_MESH = 2
        val MODE_UPDATE_MESH = 4
        val MODE_AUTO_CONNECT_MESH = 8
        val MODE_OTA = 16

        val AUTO_REFRESH_NOTIFICATION_DELAY = 2 * 1000
        val CHECK_OFFLINE_TIME = 10 * 1000
        val MIN_SCAN_PERIOD = 10 * 1000

        private val STATE_PENDING = 1
        private val STATE_RUNNING = 2

        //自动连接时最大重连次数
        private val CONNECT_MAX_RETRY = 2
    }
}
