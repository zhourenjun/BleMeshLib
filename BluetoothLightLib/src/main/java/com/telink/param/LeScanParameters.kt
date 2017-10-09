package com.telink.param

/**
 * 扫描参数类
 * [LeScanParameters]定义了[LightService.startScan]方法的必须要设置的几项参数.
 * @see LightService.startScan
 */
class LeScanParameters : Parameters() {

    //网络名
    fun setMeshName(value: String): LeScanParameters {
        this[Parameters.PARAM_MESH_NAME] = value
        return this
    }

    //超时时间(单位秒),在这个时间段内如果没有发现任何设备将停止扫描.
    fun setTimeoutSeconds(value: Int): LeScanParameters {
        this[Parameters.PARAM_SCAN_TIMEOUT_SECONDS] = value
        return this
    }

    //踢出网络后的名称,默认值为out_of_mesh
    fun setOutOfMeshName(value: String): LeScanParameters {
        this[Parameters.Companion.PARAM_OUT_OF_MESH] = value
        return this
    }

    //扫描模式,true时扫描到一个设备就会立即停止扫描.
    fun setScanMode(singleScan: Boolean): LeScanParameters {
        this[Parameters.PARAM_SCAN_TYPE_SINGLE] = singleScan
        return this
    }

    companion object {
        fun create()= LeScanParameters()
    }
}
