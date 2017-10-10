/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import java.util.Calendar;

/**
 * 时间同步通知解析器
 */
public final class GetTimeNotificationParser extends NotificationParser<Calendar> {

    private GetTimeNotificationParser() {
    }

    public static GetTimeNotificationParser create() {
        return new GetTimeNotificationParser();
    }

    @Override
    public byte opcode() {
        return Opcode.BLE_GATT_OP_CTRL_E9.getValue();
    }

    @Override
    public Calendar parse(NotificationInfo notifyInfo) {

        byte[] params = notifyInfo.params;
        int offset = 0;

        int year = (params[offset++] & 0xFF << 8) + params[offset++];
        int month = params[offset++] & 0xFF;
        int day = params[offset++] & 0xFF;
        int hour = params[offset++] & 0xFF;
        int minute = params[offset++] & 0xFF;
        int second = params[offset] & 0xFF;

        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, hour, minute, second);

        return calendar;
    }
}
