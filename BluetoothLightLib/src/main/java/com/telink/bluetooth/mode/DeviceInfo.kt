package com.telink.bluetooth.mode

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
    var meshAddress: Int = 0

    var meshUUID: Int = 0

    // 设备的产品标识符
    var productUUID: Int = 0

    var status: Int = 0

    var longTermKey = ByteArray(16)

    //设备的firmware版本
    var firmwareRevision = ""


}
