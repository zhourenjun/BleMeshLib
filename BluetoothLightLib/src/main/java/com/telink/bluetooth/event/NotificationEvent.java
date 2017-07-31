/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.event;

import android.app.Application;

import com.telink.bluetooth.light.NotificationInfo;
import com.telink.bluetooth.light.NotificationParser;
import com.telink.bluetooth.light.Opcode;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知事件,比如设备的状态/分组信息发生变化等
 */
public class NotificationEvent extends DataEvent<NotificationInfo> {

    /**
     * 设备的状态变化事件
     */
    public static final String ONLINE_STATUS = "com.telink.bluetooth.light.EVENT_ONLINE_STATUS";
    /**
     * 分组事件
     */
    public static final String GET_GROUP = "com.telink.bluetooth.light.EVENT_GET_GROUP";
    /**
     * 闹铃事件
     */
    public static final String GET_ALARM = "com.telink.bluetooth.light.EVENT_GET_ALARM";
    /**
     * 场景事件
     */
    public static final String GET_SCENE = "com.telink.bluetooth.light.EVENT_GET_SCENE";

    /**
     * 时间同步事件
     */
    public static final String GET_TIME = "com.telink.bluetooth.light.EVENT_GET_TIME";


    /**
     * EB
     */
    public static final String USER_ALL_NOTIFY = "com.telink.bluetooth.light.USER_ALL_NOTIFY";

    /**
     * EA
     */
    public static final String USER_ALL = "com.telink.bluetooth.light.USER_ALL";
    /**
     * E1
     */
    public static final String GET_DEVICE = "com.telink.bluetooth.light.GET_DEVICE";

    /**
     * 获取设备版本号
     */
    public static final String GET_DEVICE_STATE = "com.telink.bluetooth.light.GET_DEVICE_STATE";

    /**
     * 获取MeshOTA上报的进度,与 GET_DEVICE_STATE 是同一个opCode返回
     */
    public static final String GET_MESH_OTA_PROGRESS = "com.telink.bluetooth.light.GET_MESH_OTA_PROGRESS";

    /**
     * GET_DEVICE_STATE 返回数据的第一个字节
     * 获取版本号
     */
    public static final byte DATA_GET_VERSION = 0x00;

    /**
     * GET_DEVICE_STATE 返回数据的第一个字节
     * 获取进度
     */
    public final static byte DATA_GET_MESH_OTA_PROGRESS = 0x04;

    /**
     * GET_DEVICE_STATE 返回数据的第一个字节
     * 获取OTA状态信息
     */
    public final static byte DATA_GET_OTA_STATE = 0x05;


    /**
     * READ_ST_IDLE = 0,    // 没有处于mesh ota状态
     * READ_ST_SLAVE = 1,       // 处于正在接收mesh ota firmware的状态，并且会保存该firmware数据
     * READ_ST_MASTER = 2,    // 处于正在发送mesh ota firmware的状态
     * READ_ST_ONLY_RELAY = 3,    //处于正在接收mesh ota firmware的状态，但是不会保存该firmware数据(比如版本号不符合等)，字节把该数据relay出去。
     */
    public final static byte OTA_STATE_IDLE = 0;
    public final static byte OTA_STATE_SLAVE = 1;
    public final static byte OTA_STATE_MASTER = 2;
    public final static byte OTA_STATE_ONLY_RELAY = 3;


    private static final Map<Byte, String> EVENT_MAPPING = new HashMap<>();

    static {
        register(Opcode.BLE_GATT_OP_CTRL_DC, ONLINE_STATUS);
        register(Opcode.BLE_GATT_OP_CTRL_D4, GET_GROUP);
        register(Opcode.BLE_GATT_OP_CTRL_E7, GET_ALARM);
        register(Opcode.BLE_GATT_OP_CTRL_E9, GET_TIME);
        register(Opcode.BLE_GATT_OP_CTRL_C1, GET_SCENE);
        register(Opcode.BLE_GATT_OP_CTRL_C8, GET_DEVICE_STATE);
        register(Opcode.BLE_GATT_OP_CTRL_EB, USER_ALL_NOTIFY);
        register(Opcode.BLE_GATT_OP_CTRL_EA, USER_ALL);
        register(Opcode. BLE_GATT_OP_CTRL_E1, GET_DEVICE);
    }

    /**
     * 操作码
     */
    protected int opcode;
    /**
     * 源地址,即设备/组地址
     */
    protected int src;

    public NotificationEvent(Object sender, String type, NotificationInfo args) {
        super(sender, type, args);
        this.opcode = args.opcode;
        this.src = args.src;
    }

    /**
     * 注册事件类型
     *
     * @param opcode
     * @param eventType
     * @return
     */
    public static boolean register(byte opcode, String eventType) {
        opcode = (byte) (opcode & 0xFF);
        synchronized (NotificationEvent.class) {
            if (EVENT_MAPPING.containsKey(opcode))
                return false;
            EVENT_MAPPING.put(opcode, eventType);
            return true;
        }
    }

    /**
     * 注册事件类型
     *
     * @param opcode
     * @param eventType
     * @return
     * @see NotificationEvent#register(byte, String)
     */
    public static boolean register(Opcode opcode, String eventType) {
        return register(opcode.getValue(), eventType);
    }

    /**
     * 获取事件类型
     *
     * @param opcode 操作码
     * @return
     */
    public static String getEventType(byte opcode) {
        opcode = (byte) (opcode & 0xFF);
        synchronized (NotificationEvent.class) {
            if (EVENT_MAPPING.containsKey(opcode))
                return EVENT_MAPPING.get(opcode);
        }
        return null;
    }

    public static String getEventType(Opcode opcode) {
        return getEventType(opcode.getValue());
    }

    public static NotificationEvent newInstance(Application sender, String type, NotificationInfo args) {
        return new NotificationEvent(sender, type, args);
    }

    public Object parse() {
        NotificationParser parser = NotificationParser.get(this.opcode);
        return parser == null ? null : parser.parse(this.args);
    }
}
