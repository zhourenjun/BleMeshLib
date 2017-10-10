/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

/**
 * 自动刷新Notify参数
 *
 * @see LightService#autoRefreshNotify(Parameters)
 */
public final class LeRefreshNotifyParameters extends Parameters {

    /**
     * 创建{@link LeRefreshNotifyParameters}实例
     *
     * @return
     */
    public static LeRefreshNotifyParameters create() {
        return new LeRefreshNotifyParameters();
    }

    /**
     * 刷新次数
     *
     * @param value
     * @return
     */
    public LeRefreshNotifyParameters setRefreshRepeatCount(int value) {
        this.set(PARAM_AUTO_REFRESH_NOTIFICATION_REPEAT, value);
        return this;
    }

    /**
     * 间隔时间,单位毫秒
     *
     * @param value
     * @return
     */
    public LeRefreshNotifyParameters setRefreshInterval(int value) {
        this.set(PARAM_AUTO_REFRESH_NOTIFICATION_DELAY, value);
        return this;
    }
}
