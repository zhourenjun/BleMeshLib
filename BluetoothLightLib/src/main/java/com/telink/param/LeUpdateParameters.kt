package com.telink.param

import com.telink.mode.DeviceInfo
import java.util.*

/**
 * 更新网络参数
 * @see LightService.updateMesh
 */
class LeUpdateParameters : Parameters() {

    // 旧的网络名
    fun setOldMeshName(value: String): LeUpdateParameters {
        this[Parameters.PARAM_MESH_NAME] = value
        return this
    }

    // 新的网络名
    fun setNewMeshName(value: String): LeUpdateParameters {
        this[Parameters.PARAM_NEW_MESH_NAME] = value
        return this
    }

    //旧的密码
    fun setOldPassword(value: String): LeUpdateParameters {
        this[Parameters.PARAM_MESH_PASSWORD] = value
        return this
    }

    //新的密码
    fun setNewPassword(value: String): LeUpdateParameters {
        this[Parameters.PARAM_NEW_PASSWORD] = value
        return this
    }

    //LTK,如果不设置将使用厂商默认值,即[Manufacture.getFactoryLtk]
    fun setLtk(value: ByteArray): LeUpdateParameters {
        this[Parameters.PARAM_LONG_TERM_KEY] = value
        return this
    }

    //更新的设备列表
    fun setUpdateDeviceList(vararg value: DeviceInfo): LeUpdateParameters {
        this[Parameters.PARAM_DEVICE_LIST] = Arrays.asList(*value)
        return this
    }

    companion object {
        fun create()= LeUpdateParameters()
    }
}
