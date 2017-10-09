package com.telink.bluetooth.param

/**
 * 自动重连参数
 * [LeAutoConnectParameters]定义了[LightService.autoConnect] 方法的必须要设置的几项参数.
 * @see LightService.autoConnect
 */
class LeAutoConnectParameters : Parameters() {

    // 网络名
    fun setMeshName(value: String): LeAutoConnectParameters {
        this[Parameters.PARAM_MESH_NAME] = value
        return this
    }

    // 密码
    fun setPassword(value: String): LeAutoConnectParameters {
        this[Parameters.PARAM_MESH_PASSWORD] = value
        return this
    }

    /**
     * 是否在登录后开启打开Notification
     */
    fun autoEnableNotification(value: Boolean): LeAutoConnectParameters {
        this[Parameters.PARAM_AUTO_ENABLE_NOTIFICATION] = value
        return this
    }

    /**
     * 连接超时时间,单位秒.
     */
    fun setTimeoutSeconds(timeoutSeconds: Int): LeAutoConnectParameters {
        this[Parameters.PARAM_TIMEOUT_SECONDS] = timeoutSeconds
        return this
    }

    /**
     * 自动连接时，连接指定设备 NULL,表示不指定
     */
    fun setConnectMac(mac: String): LeAutoConnectParameters {
        this[Parameters.PARAM_AUTO_CONNECT_MAC] = mac
        return this
    }

    companion object {
        fun create()= LeAutoConnectParameters()
    }
}
