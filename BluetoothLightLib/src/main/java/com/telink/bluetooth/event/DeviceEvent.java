/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.event;

import com.telink.bluetooth.light.DeviceInfo;

/**
 * 设备事件
 */
public class DeviceEvent extends DataEvent<DeviceInfo> {

    /**
     * 当设备的状态发生改变时,会分发此事件.可以根据事件参数{@link DeviceInfo#status}获取状态.
     *
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_CONNECTING
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_CONNECTED
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_LOGINING
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_LOGIN
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_LOGOUT
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_UPDATING_MESH
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_UPDATE_MESH_COMPLETED
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_UPDATE_MESH_FAILURE
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_UPDATE_ALL_MESH_COMPLETED
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_GET_LTK_COMPLETED
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_GET_LTK_FAILURE
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_GET_FIRMWARE_COMPLETED
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_OTA_PROGRESS
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_OTA_COMPLETED
     * @see com.telink.bluetooth.light.LightAdapter#STATUS_OTA_FAILURE
     */
    public static final String STATUS_CHANGED = "com.telink.bluetooth.light.EVENT_STATUS_CHANGED";
    /**
     * 当前连接的设备改变时分发此事件
     */
    public static final String CURRENT_CONNECT_CHANGED = "com.telink.bluetooth.light.EVENT_CURRENT_CONNECT_CHANGED";

    public DeviceEvent(Object sender, String type, DeviceInfo args) {
        super(sender, type, args);
    }

    public static DeviceEvent newInstance(Object sender, String type, DeviceInfo args) {
        return new DeviceEvent(sender, type, args);
    }
}