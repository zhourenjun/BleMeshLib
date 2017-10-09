package com.telink.bluetooth.param

import java.util.*

open class Parameters {

    private val mParams = HashMap<String, Any>()

    init {
        set(PARAM_OUT_OF_MESH,"out_of_mesh")
    }

    operator fun set(key: String, value: Any): Parameters {
        mParams.put(key, value)
        return this
    }

    operator fun get(key: String) = mParams[key]


    fun getBytes(key: String) = if (mParams.containsKey(key)) mParams[key] as ByteArray else null

    fun getInt(key: String, defaultValue: Int) = if (mParams.containsKey(key)) mParams[key] as Int else defaultValue

    fun getInt(key: String) = getInt(key, 0)

    fun getBoolean(key: String, defaultValue: Boolean) = if (mParams.containsKey(key)) mParams[key] as Boolean else defaultValue

    fun getString(key: String) = if (mParams.containsKey(key)) mParams[key] as String else null

    fun getBoolean(key: String) = getBoolean(key, false)

    operator fun contains(key: String) = mParams.containsKey(key)

    fun clear() {
        mParams.clear()
    }

    companion object {
        val PARAM_MESH_NAME = "param_mesh_name"
        val PARAM_OUT_OF_MESH = "param_out_of_mesh"
        val PARAM_MESH_PASSWORD = "param_mesh_password"
        val PARAM_NEW_MESH_NAME = "param_new_mesh_name"
        val PARAM_NEW_PASSWORD = "param_new_password"
        val PARAM_TIMEOUT_SECONDS = "param_timeout_seconds"
        val PARAM_LONG_TERM_KEY = "param_long_term_key"
        val PARAM_AUTO_REFRESH_NOTIFICATION_REPEAT = "param_auto_refresh_notification_repeat"
        val PARAM_AUTO_REFRESH_NOTIFICATION_DELAY = "param_auto_refresh_notification_delay"
        val PARAM_DEVICE_LIST = "param_device_list"
        val PARAM_SCAN_TIMEOUT_SECONDS = "param_scan_timeout_seconds"
        val PARAM_SCAN_TYPE_SINGLE = "param_scan_type_single"
        val PARAM_OFFLINE_TIMEOUT_SECONDS = "param_offline_timeout_seconds"
        val PARAM_AUTO_ENABLE_NOTIFICATION = "param_auto_enable_notification"
        val PARAM_AUTO_CONNECT_MAC = "param_auto_connect_mac"

        fun newInstance() = Parameters()

        fun createScanParameters() = LeScanParameters.create()

        fun createUpdateParameters() = LeUpdateParameters.create()

        fun createAutoConnectParameters() = LeAutoConnectParameters.create()

        fun createRefreshNotifyParameters() = LeRefreshNotifyParameters.create()

        fun createOtaParameters() = LeOtaParameters.create()
    }
}
