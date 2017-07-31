/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import com.telink.util.NumberUtils;

/**
 * 闹铃通知解析器
 */
public final class GetAlarmNotificationParser extends NotificationParser<GetAlarmNotificationParser.AlarmInfo> {

    private GetAlarmNotificationParser() {
    }

    public static GetAlarmNotificationParser create() {
        return new GetAlarmNotificationParser();
    }

    @Override
    public byte opcode() {
        return Opcode.BLE_GATT_OP_CTRL_E7.getValue();
    }

    @Override
    public AlarmInfo parse(NotificationInfo notifyInfo) {

        byte[] params = notifyInfo.params;
        int offset = 8;
        int total = params[offset] & 0xFF;

        if (total == 0)
            return null;

        offset = 1;
        int index = params[offset++] & 0xFF;
        byte data = (byte) (params[offset] & 0xFF);
        offset = 3;
        int sceneId = params[offset] & 0xFF;

        int action = NumberUtils.byteToInt(data, 0, 3);
        int type = NumberUtils.byteToInt(data, 4, 6);
        int status = data >> 7 & 0x01;
        long time = NumberUtils.bytesToLong(params, 3, 5);

        AlarmInfo alarmInfo = new AlarmInfo();
        alarmInfo.index = index;
        alarmInfo.total = total;
        alarmInfo.action = AlarmAction.valueOf(action);
        alarmInfo.type = AlarmType.valueOf(type);
        alarmInfo.status = AlarmStatus.valueOf(status);
        alarmInfo.time = time;
        alarmInfo.sceneId = sceneId;

        return alarmInfo;
    }

    public enum AlarmAction {
        OFF(0), ON(1), SCENE(2);

        private final int value;

        AlarmAction(int value) {
            this.value = value;
        }

        static public AlarmAction valueOf(int value) {

            AlarmAction[] values = AlarmAction.values();

            for (AlarmAction action : values) {
                if (value == action.getValue())
                    return action;
            }

            return null;
        }

        public int getValue() {
            return value;
        }
    }

    public enum AlarmType {
        DAY(0), WEEK(1),;

        private final int value;

        AlarmType(int value) {
            this.value = value;
        }

        static public AlarmType valueOf(int value) {

            AlarmType[] values = AlarmType.values();

            for (AlarmType type : values) {
                if (value == type.getValue())
                    return type;
            }

            return null;
        }

        public int getValue() {
            return value;
        }
    }

    public enum AlarmStatus {
        ENABLE(1), DISABLE(0),;

        private final int value;

        AlarmStatus(int value) {
            this.value = value;
        }

        static public AlarmStatus valueOf(int value) {

            AlarmStatus[] values = AlarmStatus.values();

            for (AlarmStatus status : values) {
                if (value == status.getValue())
                    return status;
            }

            return null;
        }

        public int getValue() {
            return value;
        }
    }

    public final class AlarmInfo {

        public int index;
        public int total;
        public AlarmAction action;
        public AlarmType type;
        public AlarmStatus status;
        public long time;
        public int sceneId;

        public int getMonth() {
            return this.type == AlarmType.DAY ? (int) (this.time >> 32 & 0x0C) : 0;
        }

        public int getDayOrWeek() {
            return (int) (this.time >> 24 & 0xFF);
        }

        public int getHour() {
            return (int) (this.time >> 16 & 0xFF);
        }

        public int getMinute() {
            return (int) (this.time >> 8 & 0xFF);
        }

        public int getSecond() {
            return (int) (this.time & 0xFF);
        }
    }
}
