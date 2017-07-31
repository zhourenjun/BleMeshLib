/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

/**
 * 扫描参数类
 * <p>{@link LeScanParameters}定义了{@link LightService#startScan(Parameters)}方法的必须要设置的几项参数.
 *
 * @see LightService#startScan(Parameters)
 */
public final class LeScanParameters extends Parameters {

    /**
     * 创建LeScanParameters实例
     *
     * @return
     */
    public static LeScanParameters create() {
        return new LeScanParameters();
    }

    /**
     * 网络名
     *
     * @param value
     * @return
     */
    public LeScanParameters setMeshName(String value) {
        this.set(Parameters.PARAM_MESH_NAME, value);
        return this;
    }

    /**
     * 超时时间(单位秒),在这个时间段内如果没有发现任何设备将停止扫描.
     *
     * @param value
     * @return
     */
    public LeScanParameters setTimeoutSeconds(int value) {
        this.set(Parameters.PARAM_SCAN_TIMEOUT_SECONDS, value);
        return this;
    }

    /**
     * 踢出网络后的名称,默认值为out_of_mesh
     *
     * @param value
     * @return
     */
    public LeScanParameters setOutOfMeshName(String value) {
        this.set(PARAM_OUT_OF_MESH, value);
        return this;
    }

    /**
     * 扫描模式,true时扫描到一个设备就会立即停止扫描.
     *
     * @param singleScan
     * @return
     */
    public LeScanParameters setScanMode(boolean singleScan) {
        this.set(Parameters.PARAM_SCAN_TYPE_SINGLE, singleScan);
        return this;
    }

}
