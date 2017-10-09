package com.telink.bluetooth.mode

import java.io.Serializable

/**
 * NotificationInfo封装收到的蓝牙Notification信息
 */
class NotificationInfo : Serializable {

    //操作码
    var opcode: Int = 0
    //源地址
    var src: Int = 0
    // 参数
    var params = ByteArray(10)
    // 当前连接的设备
    var deviceInfo: DeviceInfo? = null
}
