package com.telink.bluetooth.param

/**
 * 自动刷新Notify参数
 * @see LightService.autoRefreshNotify
 */
class LeRefreshNotifyParameters : Parameters() {

    // 刷新次数
    fun setRefreshRepeatCount(value: Int): LeRefreshNotifyParameters {
        this[Parameters.PARAM_AUTO_REFRESH_NOTIFICATION_REPEAT] = value
        return this
    }

    //间隔时间,单位毫秒
    fun setRefreshInterval(value: Int): LeRefreshNotifyParameters {
        this[Parameters.PARAM_AUTO_REFRESH_NOTIFICATION_DELAY] = value
        return this
    }

    companion object {
        fun create()= LeRefreshNotifyParameters()
    }
}
