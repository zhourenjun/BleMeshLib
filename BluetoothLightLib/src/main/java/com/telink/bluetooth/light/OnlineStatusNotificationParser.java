/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import java.util.ArrayList;
import java.util.List;

public final class OnlineStatusNotificationParser extends NotificationParser<List<OnlineStatusNotificationParser.DeviceNotificationInfo>> {

    private OnlineStatusNotificationParser() {
    }

    public static OnlineStatusNotificationParser create() {
        return new OnlineStatusNotificationParser();
    }

    @Override
    public byte opcode() {
        return Opcode.BLE_GATT_OP_CTRL_DC.getValue();
    }

    @Override
    public List<DeviceNotificationInfo> parse(NotificationInfo notifyInfo) {

        byte[] params = notifyInfo.params;

        int meshAddress;
        int status;
        int brightness;
        int reserve;

        int position = 0;
        int packetSize = 4;
        int length = params.length;

        List<DeviceNotificationInfo> notificationInfoList = null;
        DeviceNotificationInfo deviceNotifyInfo;

        while ((position + packetSize) < length) {

            meshAddress = params[position++];
            status = params[position++];
            brightness = params[position++];
            reserve = params[position++];

            meshAddress = meshAddress & 0xFF;

            if (meshAddress == 0x00
                    || (meshAddress == 0xFF && brightness == 0xFF))
                break;

            if (notificationInfoList == null)
                notificationInfoList = new ArrayList<>();

            deviceNotifyInfo = new DeviceNotificationInfo();
            deviceNotifyInfo.meshAddress = meshAddress;
            deviceNotifyInfo.brightness = brightness;
            deviceNotifyInfo.reserve = reserve;
            deviceNotifyInfo.status = status;

            if (status == 0) {
                deviceNotifyInfo.connectionStatus = ConnectionStatus.OFFLINE;
            } else if (brightness != 0) {
                deviceNotifyInfo.connectionStatus = ConnectionStatus.ON;
            } else {
                deviceNotifyInfo.connectionStatus = ConnectionStatus.OFF;
            }

            notificationInfoList.add(deviceNotifyInfo);
        }

        return notificationInfoList;
    }

    public final class DeviceNotificationInfo {
        public int meshAddress;
        public int status;
        public int brightness;
        public int reserve;
        public ConnectionStatus connectionStatus = ConnectionStatus.OFFLINE;
    }
}
