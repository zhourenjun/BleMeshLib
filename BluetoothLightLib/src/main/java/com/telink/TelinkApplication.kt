package com.telink

import android.app.Application
import android.content.*
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import com.telink.event.*
import com.telink.light.LightAdapter
import com.telink.light.LightService
import com.telink.mode.DeviceInfo
import com.telink.mode.NotificationInfo
import com.telink.parser.*
import com.telink.util.BleLog
import com.telink.util.Strings
import kotlin.properties.Delegates

class TelinkApplication : Application() {

    companion object {
        private var mThis: TelinkApplication by Delegates.notNull()
    }

    private val mEventBus = EventBus<String>()
    private lateinit var mContext: Context
    private var serviceStarted = false
    private var serviceConnected = false
    private lateinit var mLightReceiver: BroadcastReceiver

    private val mServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            serviceConnected(name, service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceDisconnected(name)
        }
    }

    /**
     * 当前连接的设备
     */
    var connectDevice: DeviceInfo? = null
        private set


    override fun onCreate() {
        mThis = this
        mContext = this
        super.onCreate()
        BleLog.d("TelinkApp Created.")
    }

    /**
     * 执行初始化,APP启动时调用
     */
    fun doInit(clazz: Class<out LightService>? = null) {
        LocalBroadcastManager.getInstance(mContext).registerReceiver(makeLightReceiver(), makeLightFilter())
        if (clazz != null) startLightService(clazz)
        registerNotificationParser(OnlineStatusNotificationParser.create())
        registerNotificationParser(GetGroupNotificationParser.create())
        registerNotificationParser(GetAlarmNotificationParser.create())
        registerNotificationParser(GetSceneNotificationParser.create())
        registerNotificationParser(GetTimeNotificationParser.create())
    }

    /**
     * 销毁,当退出APP时调用此方法
     */
    fun doDestroy() {
        stopLightService()
        if (mLightReceiver != null) LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mLightReceiver)
        removeEventListeners()
        connectDevice = null
        serviceStarted = false
        serviceConnected = false
    }

    private fun makeLightReceiver(): BroadcastReceiver {
        if (mLightReceiver == null)
            mLightReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        LightService.ACTION_NOTIFICATION -> onNotify(intent)
                        LightService.ACTION_STATUS_CHANGED -> onStatusChanged(intent)
                        LightService.ACTION_UPDATE_MESH_COMPLETED -> onUpdateMeshCompleted()
                        LightService.ACTION_OFFLINE -> onMeshOffline()
                        LightService.ACTION_LE_SCAN -> onLeScan(intent)
                        LightService.ACTION_LE_SCAN_TIMEOUT -> onLeScanTimeout()
                        LightService.ACTION_SCAN_COMPLETED -> onLeScanCompleted()
                        LightService.ACTION_ERROR -> onError(intent)
                    }
                }
            }

        return mLightReceiver
    }

    private fun makeLightFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(LightService.ACTION_LE_SCAN)
        filter.addAction(LightService.ACTION_SCAN_COMPLETED)
        filter.addAction(LightService.ACTION_LE_SCAN_TIMEOUT)
        filter.addAction(LightService.ACTION_NOTIFICATION)
        filter.addAction(LightService.ACTION_STATUS_CHANGED)
        filter.addAction(LightService.ACTION_UPDATE_MESH_COMPLETED)
        filter.addAction(LightService.ACTION_OFFLINE)
        filter.addAction(LightService.ACTION_ERROR)
        return filter
    }

    /**
     * 添加一个事件监听器
     */
    fun addEventListener(eventType: String, listener: EventListener<String>) {
        mEventBus.addEventListener(eventType, listener)
    }

    /**
     * 移除事件监听器
     */
    fun removeEventListener(listener: EventListener<String>) {
        mEventBus.removeEventListener(listener)
    }

    /**
     * 从事件监听器中移除指定的事件
     */
    fun removeEventListener(eventType: String, listener: EventListener<String>) {
        mEventBus.removeEventListener(eventType, listener)
    }

    /**
     * 移除所有的事件监听器
     */
    private fun removeEventListeners() {
        mEventBus.removeEventListeners()
    }

    /**
     * 分发事件
     */
    private fun dispatchEvent(event: Event<String>) {
        mEventBus.dispatchEvent(event)
    }


    /**
     * 启动LightService
     */
    private fun startLightService(clazz: Class<out LightService>) {
        if (serviceStarted || serviceConnected) return
        serviceStarted = true
        val service = Intent(mContext, clazz)
        bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 停止LightService
     */
    private fun stopLightService() {
        if (!serviceStarted) return
        serviceStarted = false
        if (serviceConnected) unbindService(mServiceConnection)
    }

    private fun serviceConnected(name: ComponentName, service: IBinder) {
        BleLog.d("service connected --> " + name.shortClassName)
        serviceConnected = true
        dispatchEvent(ServiceEvent.newInstance(this, ServiceEvent.SERVICE_CONNECTED, service))
    }

    private fun serviceDisconnected(name: ComponentName) {
        BleLog.d("service disconnected --> " + name.shortClassName)
        serviceConnected = false
        dispatchEvent(ServiceEvent.newInstance(this, ServiceEvent.SERVICE_DISCONNECTED, null))
    }


    private fun onLeScan(intent: Intent) {
        val deviceInfo = intent.getSerializableExtra(LightService.EXTRA_DEVICE) as DeviceInfo
        dispatchEvent(LeScanEvent.newInstance(this, LeScanEvent.LE_SCAN, deviceInfo))
    }

    private fun onLeScanCompleted() {
        dispatchEvent(LeScanEvent.newInstance(this, LeScanEvent.LE_SCAN_COMPLETED, null))
    }

    private fun onLeScanTimeout() {
        dispatchEvent(LeScanEvent.newInstance(this, LeScanEvent.LE_SCAN_TIMEOUT, null))
    }

    private fun onStatusChanged(intent: Intent) {
        val deviceInfo = intent.getSerializableExtra(LightService.EXTRA_DEVICE) as DeviceInfo
        if (deviceInfo.status == LightAdapter.STATUS_LOGIN) {
            connectDevice = deviceInfo
            dispatchEvent(DeviceEvent.newInstance(this, DeviceEvent.CURRENT_CONNECT_CHANGED, deviceInfo))
        } else if (deviceInfo.status == LightAdapter.STATUS_LOGOUT) {
            connectDevice = null
        }

        dispatchEvent(DeviceEvent.newInstance(this, DeviceEvent.STATUS_CHANGED, deviceInfo))
    }

    private fun onUpdateMeshCompleted() {
        dispatchEvent(MeshEvent.newInstance(this, MeshEvent.UPDATE_COMPLETED, -1))
    }

    private fun onMeshOffline() {
        dispatchEvent(MeshEvent.newInstance(this, MeshEvent.OFFLINE, -1))
    }

    private fun onError(intent: Intent) {
        val errorCode = intent.getIntExtra(LightService.EXTRA_ERROR_CODE, -1)
        dispatchEvent(MeshEvent.newInstance(this, MeshEvent.ERROR, errorCode))
    }

    private fun onNotify(intent: Intent) {
        val notifyInfo = intent.getSerializableExtra(LightService.EXTRA_NOTIFY) as NotificationInfo
        onNotify(notifyInfo)
    }

    private fun onNotify(notifyInfo: NotificationInfo) {
        val opcode = notifyInfo.opcode
        val eventType = NotificationEvent.getEventType(opcode.toByte())
        if (Strings.isEmpty(eventType)) return
        BleLog.e("postCommand event : $eventType--$opcode")
        val event = NotificationEvent.newInstance(this, eventType!!, notifyInfo)
        event.threadMode = Event.ThreadMode.Background
        dispatchEvent(event)
    }


    /**
     * 注册通知解析器
     */
    private fun registerNotificationParser(parser: NotificationParser<*>) {
        NotificationParser.register(parser)
    }


}
