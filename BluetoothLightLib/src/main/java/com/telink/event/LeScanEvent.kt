package com.telink.event

import com.telink.mode.DeviceInfo

/**
 * 扫描事件
 */
class LeScanEvent(sender: Any, type: String, args: DeviceInfo?) : DataEvent<DeviceInfo>(sender, type, args) {
    companion object {

        //扫描到设备
        val LE_SCAN = "event_le_scan"
        val LE_SCAN_COMPLETED = "event_le_scan_completed"
        //扫描超时
        val LE_SCAN_TIMEOUT = "event_le_scan_timeout"

        fun newInstance(sender: Any, type: String, args: DeviceInfo?): LeScanEvent {
            return LeScanEvent(sender, type, args)
        }
    }
}
