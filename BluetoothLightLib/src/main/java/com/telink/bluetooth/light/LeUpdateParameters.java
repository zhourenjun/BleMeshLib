/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import java.util.Arrays;

/**
 * 更新网络参数
 *
 * @see LightService#updateMesh(Parameters)
 */
public final class LeUpdateParameters extends Parameters {

    /**
     * 创建{@link LeOtaParameters}实例
     *
     * @return
     */
    public static LeUpdateParameters create() {
        return new LeUpdateParameters();
    }

    /**
     * 旧的网络名
     *
     * @param value
     * @return
     */
    public LeUpdateParameters setOldMeshName(String value) {
        this.set(PARAM_MESH_NAME, value);
        return this;
    }

    /**
     * 新的网络名
     *
     * @param value
     * @return
     */
    public LeUpdateParameters setNewMeshName(String value) {
        this.set(PARAM_NEW_MESH_NAME, value);
        return this;
    }

    /**
     * 旧的密码
     *
     * @param value
     * @return
     */
    public LeUpdateParameters setOldPassword(String value) {
        this.set(PARAM_MESH_PASSWORD, value);
        return this;
    }

    /**
     * 新的密码
     *
     * @param value
     * @return
     */
    public LeUpdateParameters setNewPassword(String value) {
        this.set(PARAM_NEW_PASSWORD, value);
        return this;
    }

    /**
     * LTK,如果不设置将使用厂商默认值,即{@link Manufacture#getFactoryLtk()}
     *
     * @param value
     * @return
     */
    public LeUpdateParameters setLtk(byte[] value) {
        this.set(PARAM_LONG_TERM_KEY, value);
        return this;
    }

    /**
     * 更新的设备列表
     *
     * @param value
     * @return
     * @see DeviceInfo
     */
    public LeUpdateParameters setUpdateDeviceList(DeviceInfo... value) {
        this.set(PARAM_DEVICE_LIST, Arrays.asList(value));
        return this;
    }
}
