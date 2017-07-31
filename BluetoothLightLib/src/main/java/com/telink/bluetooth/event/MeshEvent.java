/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.event;

/**
 * 网络事件
 */
public class MeshEvent extends DataEvent<Integer> {

    public static final String UPDATE_COMPLETED = "com.telink.bluetooth.light.EVENT_UPDATE_COMPLETED";
    /**
     * 连接到不任何设备的时候分发此事件
     */
    public static final String OFFLINE = "com.telink.bluetooth.light.EVENT_OFFLINE";
    /**
     * 出现异常时分发此事件,比如蓝牙关闭了
     */
    public static final String ERROR = "com.telink.bluetooth.light.EVENT_ERROR";

    public MeshEvent(Object sender, String type, Integer args) {
        super(sender, type, args);
    }

    public static MeshEvent newInstance(Object sender, String type, Integer args) {
        return new MeshEvent(sender, type, args);
    }
}
