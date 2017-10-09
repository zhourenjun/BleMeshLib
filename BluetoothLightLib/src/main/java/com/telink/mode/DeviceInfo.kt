package com.telink.mode

import java.io.Serializable

/**
 * 设备信息类
 */
open class DeviceInfo : Serializable {
    // Mac地址
    var macAddress = ""

    //设备名称
    var deviceName = ""

    // 网络名称
    var meshName = ""

    // 网络地址
    var meshAddress = 0

    var meshUUID = 0

    // 设备的产品标识符
    var productUUID = 0

    var status = 0

    var longTermKey = ByteArray(16)

    //设备的firmware版本
    var firmwareRevision = ""

    var rssi = 0
}
