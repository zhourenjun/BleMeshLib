package com.telink.bluetooth.event

import android.os.IBinder

/**
 * LightService事件
 */
class ServiceEvent(sender: Any, type: String, args: IBinder?) : DataEvent<IBinder>(sender, type, args) {
    companion object {

        //服务启动
        val SERVICE_CONNECTED = "event_service_connected"
        //服务关闭
        val SERVICE_DISCONNECTED = "event_service_disconnected"

        fun newInstance(sender: Any, type: String, args: IBinder?): ServiceEvent {
            return ServiceEvent(sender, type, args)
        }
    }
}