package com.telink.bluetooth.param

import com.telink.bluetooth.mode.OtaDeviceInfo

/**
 * OTA参数
 * @see LightService.startOta
 */
class LeOtaParameters : Parameters() {

    //网络名
    fun setMeshName(value: String): LeOtaParameters {
        this[Parameters.PARAM_MESH_NAME] = value
        return this
    }

    //密码
    fun setPassword(value: String): LeOtaParameters {
        this[Parameters.PARAM_MESH_PASSWORD] = value
        return this
    }

    // 扫描超时时间,单位秒
    fun setLeScanTimeoutSeconds(value: Int): LeOtaParameters {
        this[Parameters.PARAM_SCAN_TIMEOUT_SECONDS] = value
        return this
    }

    // 要进行OTA的设备
    fun setDeviceInfo(value: OtaDeviceInfo): LeOtaParameters {
        this[Parameters.PARAM_DEVICE_LIST] = value
        return this
    }

    companion object {
        fun create() = LeOtaParameters()
    }
}
