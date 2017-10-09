package com.telink.bluetooth.event

import com.telink.bluetooth.mode.DeviceInfo

/**
 * 设备事件
 */
class DeviceEvent(sender: Any, type: String, args: DeviceInfo) : DataEvent<DeviceInfo>(sender, type, args) {
    companion object {
        /**
         * 当设备的状态发生改变时,会分发此事件.可以根据事件参数[DeviceInfo.status]获取状态.
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_CONNECTING
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_CONNECTED
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_LOGINING
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_LOGIN
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_LOGOUT
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_UPDATING_MESH
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_UPDATE_MESH_COMPLETED
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_UPDATE_MESH_FAILURE
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_UPDATE_ALL_MESH_COMPLETED
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_GET_LTK_COMPLETED
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_GET_LTK_FAILURE
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_GET_FIRMWARE_COMPLETED
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_OTA_PROGRESS
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_OTA_COMPLETED
         * @see com.telink.bluetooth.light.LightAdapter.STATUS_OTA_FAILURE
         */
        val STATUS_CHANGED = "event_status_changed"
        // 当前连接的设备改变时分发此事件
        val CURRENT_CONNECT_CHANGED = "event_current_connect_changed"

        fun newInstance(sender: Any, type: String, args: DeviceInfo): DeviceEvent {
            return DeviceEvent(sender, type, args)
        }
    }
}