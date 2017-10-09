package com.telink.mode


/**
 * OTA设备信息
 */
class OtaDeviceInfo : DeviceInfo() {

    //firmware数据
    var firmware: ByteArray? = null
    //ota进度
    var progress = 0
}
