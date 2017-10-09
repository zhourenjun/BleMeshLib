package com.telink.light

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.annotation.RequiresApi
import android.support.v4.content.LocalBroadcastManager
import com.telink.mode.DeviceInfo
import com.telink.mode.NotificationInfo
import com.telink.mode.OtaDeviceInfo
import com.telink.param.Parameters

/**
 * LightService是一个抽象类,封装了扫描加灯,自动重连,设备控制等方法.
 * @see LightAdapter
 * @see LeScanParameters
 * @see LeAutoConnectParameters
 * @see LeUpdateParameters
 * @see LeOtaParameters
 * @see LeRefreshNotifyParameters
 */
abstract class LightService : Service(), LightAdapter.Callback, AdvanceStrategy.Callback {

    lateinit var adapter: LightAdapter
    lateinit var mBinder: IBinder

    /**
     * 获取当前工作模式
     * @see LightAdapter.MODE_SCAN_MESH
     * @see LightAdapter.MODE_AUTO_CONNECT_MESH
     * @see LightAdapter.MODE_UPDATE_MESH
     * @see LightAdapter.MODE_IDLE
     * @see LightAdapter.MODE_OTA
     */
    val mode = if (adapter != null) adapter.getMode() else -1

    /**
     * 是否已经登录到设备
     * @see LightAdapter.isLogin
     */
    val isLogin = adapter != null && adapter.isLogin

    /**
     * 获取当前连接设备的firmware信息
     * firmware获取成功或失败会通过[LightService.ACTION_STATUS_CHANGED]动作广播出去.
     * 广播参数[LightService.EXTRA_DEVICE]是个[DeviceInfo]类型, 通过[DeviceInfo.status]即可获取firmware是否获取成功
     * @see LightAdapter.STATUS_GET_FIRMWARE_COMPLETED
     * @see LightAdapter.STATUS_GET_FIRMWARE_FAILURE
     */
    val firmwareVersion = adapter != null && adapter.firmwareVersion

    override fun onBind(intent: Intent) = mBinder

    override fun onCreate() {
        super.onCreate()
        AdvanceStrategy.default!!.setCallback(this)
        AdvanceStrategy.default!!.onStart()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onDestroy() {
        super.onDestroy()
        AdvanceStrategy.default!!.setCallback(null!!)
        AdvanceStrategy.default!!.onStop()
        if (adapter != null) {
            adapter.stop()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            /**
     * 启动扫描
     * 通常[LightService.startScan]方法会和其他方法组合使用.
     * 比如扫描添加设备[LightService.updateMesh],扫描并ota[LightService.startOta].
     *
     * **示例.**
     * LeScanParameters params = LeScanParameters.create();
     * params.setMeshName(Manufacture.getDefault().getFactoryName());
     * params.setOutOfMeshName("out_of_mesh");
     * params.setTimeoutSeconds(5);
     * params.setScanMode(true);
     * LightService.getInstance().startScan(params);
     *
     * 启动扫描后,通过[LightService.ACTION_LE_SCAN]动作广播发现到的设备,广播参数[LightService.EXTRA_DEVICE]
     * 表示发现的设备[DeviceInfo].
     * 当扫描超过指定的时间,会发送[LightService.ACTION_LE_SCAN_TIMEOUT]动作的广播.
     * @param params [LeScanParameters]类型
     * @see LeScanParameters
     * @see com.telink.bluetooth.event.LeScanEvent
     * @see com.telink.bluetooth.event.DeviceEvent
     * @see com.telink.bluetooth.event.MeshEvent
     */
     fun startScan(params: Parameters) {
        if (adapter == null)
            return
        adapter.startScan(params, this)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            /**
     * 自动重连
     * 当启动自动重连时,如果当前连接的设备连接断开了,会自动扫描一个设备进行连接.
     * 通常[LightService.autoConnect]会和[LightService.autoRefreshNotify]成对出现.
     * 连接设备的状态会通过[LightService.ACTION_STATUS_CHANGED]动作广播出来
     *
     * **示例**.
     * LightService service = SmartLightService.getInstance();
     * Mesh mesh = this.mApp.getCurrentMesh();
     * LeAutoConnectParameters params = LeAutoConnectParameters.create();
     * params.setMeshName(mesh.getMeshName());
     * params.setPassword(mesh.getPassword());
     * params.autoEnableNotification(true);
     * service.autoConnect(params);
     * ......
     * LeRefreshNotifyParameters refreshNotifyParams = LeRefreshNotifyParameters.create();
     * refreshNotifyParams.setRefreshRepeatCount(2);
     * refreshNotifyParams.setRefreshInterval(1000);
     * service.autoRefreshNotify(refreshNotifyParams);
     *
     * @param params [LeAutoConnectParameters]类型参数
     * @see LeAutoConnectParameters
     * @see com.telink.bluetooth.event.DeviceEvent
     * @see com.telink.bluetooth.event.MeshEvent
     */
     fun autoConnect(params: Parameters) {
        if (adapter == null)
            return
        adapter.autoConnect(params, this)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            /**
     * 更新设备的名称,密码,LTK
     * **示例.**
     * LeUpdateParameters params = LeUpdateParameters.create();
     * params.setOldMeshName(Manufacture.getDefault().getFactoryName());
     * params.setOldPassword(Manufacture.getDefault().getFactoryPassword());
     * params.setNewMeshName(this.mApp.getCurrentMesh().getMeshName());
     * params.setNewPassword(this.mApp.getCurrentMesh().getPassword());
     * params.setLtk(this.mApp.getCurrentMesh().getLtk());
     * params.setUpdateDeviceList(deviceInfo);
     * SmartLightService.getInstance().idleMode(true);
     * SmartLightService.getInstance().updateMesh(params);
     *
     * @param params [LeUpdateParameters]类型
     * @see LeUpdateParameters
     * @see com.telink.bluetooth.event.DeviceEvent
     */
    fun updateMesh(params: Parameters) {
        if (adapter == null)
            return
        adapter.updateMesh(params, this)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            /**
     * 空闲模式
     * @param disconnect 是否断开当前的连接
     */
    fun idleMode(disconnect: Boolean) {
        if (adapter == null)
            return
        adapter.idleMode(disconnect)
    }

    /**
     * 向设备发送控制命令
     * 使用[android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE]方式发送.
     * **示例.**
     * LightService service = ...
     * service.sendCommandNoResponse((byte) 0xD0, 0xFFFF, new byte[]{0x01, 0x00, 0x00});
     * @param opcode  操作码
     * @param address 设备的地址
     * @param params  参数
     * @return
     */
    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray): Boolean {
        return sendCommandNoResponse(opcode, address, params, null, 0, false)
    }

    fun sendCommandNoResponseImmediate(opcode: Byte, address: Int, params: ByteArray): Boolean {
        return sendCommandNoResponse(opcode, address, params, null, 0, true)
    }

    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray, immediate: Boolean): Boolean {
        return sendCommandNoResponse(opcode, address, params, null, 0, immediate)
    }

    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray, tag: Any, immediate: Boolean): Boolean {
        return sendCommandNoResponse(opcode, address, params, tag, 0, immediate)
    }

    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray, delay: Int, immediate: Boolean): Boolean {
        return sendCommandNoResponse(opcode, address, params, null, delay, immediate)
    }

    fun sendCommandNoResponse(opcode: Byte, address: Int, params: ByteArray, tag: Any?, delay: Int, immediate: Boolean): Boolean {
        return adapter != null && AdvanceStrategy.default!!.postCommand(opcode, address, params, delay, tag!!, true, immediate)
    }

    fun autoRefreshNotify(enable: Boolean, params: Parameters) {

        if (adapter == null)
            return

        if (enable) {
            adapter.enableAutoRefreshNotify(params)
        } else {
            adapter.disableAutoRefreshNotify()
        }
    }

    /**
     * 开启自动刷新网络通知
     * [LightService.autoRefreshNotify]通常会和自动重连[LightService.autoConnect]配合使用.
     * 此方法主要是解决偶尔的设备状态收不到的问题.当启动自动刷新网络,会自动想直接连接的设备发送命令获取当前网络内的设备状态信息
     * LeRefreshNotifyParameters refreshNotifyParams = LeRefreshNotifyParameters.create();
     * refreshNotifyParams.setRefreshRepeatCount(2);
     * refreshNotifyParams.setRefreshInterval(1000);
     * service.autoRefreshNotify(refreshNotifyParams);
     * @param params [LeRefreshNotifyParameters]类型.
     * @see LeRefreshNotifyParameters
     */
    fun autoRefreshNotify(params: Parameters) {

        if (adapter == null)
            return
        adapter.enableAutoRefreshNotify(params)
    }

    /**
     * 关闭自动刷新网络通知
     */
    fun disableAutoRefreshNotify() {
        if (adapter == null)
            return
        adapter.disableAutoRefreshNotify()
    }

    /**
     * 开启通知  默认不会开启.
     */
    fun enableNotification() {
        if (adapter == null)
            return
        adapter.enableNotification()
    }

    /**
     * 关闭通知
     */
    fun disableNotification() {
        if (adapter == null)
            return
        adapter.disableNotification()
    }

    /**
     * 刷新指定的的设备状态
     * LightService service = ...;
     * //刷新0x22,0x11号设备状态
     * service.updateNotification(new byte[]{0x01, 0x22, 0x11});
     * @param params 设备地址数组
     */
    fun updateNotification(params: ByteArray) {
        if (adapter == null)
            return
        adapter.updateNotification(params)
    }

    /**
     * 刷新所有设备状态
     */
    fun updateNotification() {
        if (adapter == null)
            return
        adapter.updateNotification()
    }

    /**
     * 连接到指定的设备
     * 要连接到指定的设备需要先调用[LightService.startScan]扫描设备.
     */
    fun connect(mac: String, timeoutSeconds: Int): Boolean {
        return adapter != null && adapter.connect(mac, timeoutSeconds)
    }

    fun disconnect() {
        if (adapter != null)
            adapter.disconnect()
    }

    /**
     * 登录到当前连接的设备
     */
    fun login(meshName: ByteArray, password: ByteArray)= adapter != null && adapter.login(meshName, password)

    /**
     * 开始ota
     * OTA必要的流程,1.扫描到设备 2.连接设备并获取firmware版本作比较 3.开始ota
     * @param parameters [LeOtaParameters]类型
     * @see LeOtaParameters
     */
    fun startOta(parameters: Parameters) {
        if (adapter == null)
            return
        adapter.startOta(parameters, this)
    }

    /**
     * 当前登录的设备直接开始ota
     * @param firmware firmware的数据
     */
    fun startOta(firmware: ByteArray): Boolean {
        return adapter != null && adapter.startOta(firmware)
    }



    override fun onLeScan(light: LightPeripheral, mode: Int, scanRecord: ByteArray?): Boolean {

        val deviceInfo = DeviceInfo()
        deviceInfo.macAddress = light.macAddress
        deviceInfo.deviceName = light.deviceName
        deviceInfo.meshName = light.meshNameStr!!
        deviceInfo.meshAddress = light.meshAddress
        deviceInfo.meshUUID = light.meshUUID
        deviceInfo.productUUID = light.productUUID
        deviceInfo.status = light.status
        deviceInfo.rssi = light.rssi

        val intent = Intent()
        intent.action = ACTION_LE_SCAN
        intent.putExtra(EXTRA_MODE, mode)
        intent.putExtra(EXTRA_DEVICE, deviceInfo)

        LocalBroadcastManager.getInstance(this@LightService).sendBroadcast(intent)

        return true
    }

    override fun onStatusChanged(controller: LightController?, mode: Int, oldStatus: Int,newStatus: Int) {

        val light = controller!!.currentLight

        val intent = Intent()

        when (newStatus) {
            LightAdapter.STATUS_MESH_SCAN_TIMEOUT -> intent.action = ACTION_LE_SCAN_TIMEOUT
            LightAdapter.STATUS_MESH_SCAN_COMPLETED -> intent.action = ACTION_SCAN_COMPLETED
            LightAdapter.STATUS_MESH_OFFLINE -> intent.action = ACTION_OFFLINE
            LightAdapter.STATUS_UPDATE_ALL_MESH_COMPLETED -> intent.action = ACTION_UPDATE_MESH_COMPLETED
            LightAdapter.STATUS_OTA_PROGRESS -> {
                val deviceInfo = OtaDeviceInfo()
                deviceInfo.firmwareRevision = light?.firmwareRevision!!
                deviceInfo.macAddress = light.macAddress
                deviceInfo.progress = controller.otaProgress
                deviceInfo.status = newStatus
                intent.action = ACTION_STATUS_CHANGED
                intent.putExtra(EXTRA_MODE, mode)
                intent.putExtra(EXTRA_DEVICE, deviceInfo)
            }
            else -> {
                val deviceInfo = DeviceInfo()
                deviceInfo.macAddress = light!!.macAddress
                deviceInfo.deviceName = light.deviceName
                deviceInfo.meshName = light.meshNameStr!!
                deviceInfo.meshAddress = light.meshAddress
                deviceInfo.meshUUID = light.meshUUID
                deviceInfo.productUUID = light.productUUID
                deviceInfo.status = newStatus
                deviceInfo.firmwareRevision = light.firmwareRevision!!
                intent.action = ACTION_STATUS_CHANGED
                intent.putExtra(EXTRA_MODE, mode)
                intent.putExtra(EXTRA_DEVICE, deviceInfo)
            }
        }

        LocalBroadcastManager.getInstance(this@LightService).sendBroadcast(intent)
    }

    override fun onNotify(light: LightPeripheral, mode: Int, opcode: Int, src: Int, params: ByteArray) {

        val intent = Intent()
        intent.action = ACTION_NOTIFICATION
        intent.putExtra(EXTRA_MODE, mode)

        val notifyInfo = NotificationInfo()
        notifyInfo.src = src
        notifyInfo.opcode = opcode
        notifyInfo.params = params

        if (light != null) {
            val deviceInfo = DeviceInfo()
            deviceInfo.macAddress = light.macAddress
            deviceInfo.deviceName = light.deviceName
            deviceInfo.meshName = light.meshNameStr!!
            deviceInfo.meshAddress = light.meshAddress
            deviceInfo.meshUUID = light.meshUUID
            deviceInfo.productUUID = light.productUUID
            notifyInfo.deviceInfo = deviceInfo
        }

        intent.putExtra(EXTRA_NOTIFY, notifyInfo)

        LocalBroadcastManager.getInstance(this@LightService).sendBroadcast(intent)
    }

    override fun onCommandResponse(light: LightPeripheral, mode: Int, command: Command, success: Boolean) {

    }

    override fun onError(errorCode: Int) {
        val intent = Intent()
        intent.action = ACTION_ERROR
        intent.putExtra(EXTRA_ERROR_CODE, errorCode)
        LocalBroadcastManager.getInstance(this@LightService).sendBroadcast(intent)
    }

    override fun onCommandSampled(opcode: Byte, address: Int, params: ByteArray?, tag: Any?, delay: Int): Boolean {
        return if (adapter == null) false else adapter.sendCommandNoResponse(opcode, address, params!!, tag, delay)
    }

    companion object {
        val ACTION_LE_SCAN = "action_le_scan"
        val ACTION_SCAN_COMPLETED = "action_scan_completed"
        val ACTION_LE_SCAN_TIMEOUT = "action_le_scan_timeout"
        val ACTION_NOTIFICATION = "action_notification"
        val ACTION_STATUS_CHANGED = "action_status_changed"
        val ACTION_UPDATE_MESH_COMPLETED = "action_update_mesh_completed"
        val ACTION_OFFLINE = "action_offline"
        val ACTION_ERROR = "action_error"
        val EXTRA_MODE = "extra_mode"
        val EXTRA_DEVICE = "extra_device"
        val EXTRA_NOTIFY = "extra_notify"
        val EXTRA_ERROR_CODE = "extra_error_code"
    }
}
