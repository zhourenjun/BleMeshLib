/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import java.util.ArrayList;
import java.util.List;

/**
 * 分组通知解析器
 */
public final class GetGroupNotificationParser extends NotificationParser<List<Integer>> {

    private GetGroupNotificationParser() {
    }

    public static GetGroupNotificationParser create() {
        return new GetGroupNotificationParser();
    }

    @Override
    public byte opcode() {
        return Opcode.BLE_GATT_OP_CTRL_D4.getValue();
    }

    @Override
    public List<Integer> parse(NotificationInfo notifyInfo) {

        List<Integer> mAddress = new ArrayList<>();

        byte[] params = notifyInfo.params;
        int length = params.length;
        int position = 0;
        int address;

        while (position < length) {
            address = params[position++];
            address = address & 0xFF;

            if (address == 0xFF)
                break;

            address = address | 0x8000;
            mAddress.add(address);
        }

        return mAddress;
    }
}
