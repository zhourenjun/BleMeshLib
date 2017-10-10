/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

/**
 * OTA参数
 *
 * @see LightService#startOta(Parameters)
 */
public final class LeOtaParameters extends Parameters {

    /**
     * 创建{@link LeOtaParameters}实例
     *
     * @return
     */
    public static LeOtaParameters create() {
        return new LeOtaParameters();
    }

    /**
     * 网络名
     *
     * @param value
     * @return
     */
    public LeOtaParameters setMeshName(String value) {
        this.set(PARAM_MESH_NAME, value);
        return this;
    }

    /**
     * 密码
     *
     * @param value
     * @return
     */
    public LeOtaParameters setPassword(String value) {
        this.set(PARAM_MESH_PASSWORD, value);
        return this;
    }

    /**
     * 扫描超时时间,单位秒
     *
     * @param value
     * @return
     */
    public LeOtaParameters setLeScanTimeoutSeconds(int value) {
        this.set(PARAM_SCAN_TIMEOUT_SECONDS, value);
        return this;
    }

    /**
     * 要进行OTA的设备
     *
     * @param value
     * @return
     * @see OtaDeviceInfo
     */
    public LeOtaParameters setDeviceInfo(OtaDeviceInfo value) {
        this.set(PARAM_DEVICE_LIST, value);
        return this;
    }
}
