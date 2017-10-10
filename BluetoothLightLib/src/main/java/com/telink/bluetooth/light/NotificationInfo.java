/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * NotificationInfo封装收到的蓝牙Notification信息
 */
public final class NotificationInfo implements Parcelable {

    public static final Creator<NotificationInfo> CREATOR = new Creator<NotificationInfo>() {
        @Override
        public NotificationInfo createFromParcel(Parcel in) {
            return new NotificationInfo(in);
        }

        @Override
        public NotificationInfo[] newArray(int size) {
            return new NotificationInfo[size];
        }
    };

    /**
     * 操作码
     */
    public int opcode;
    /**
     * 源地址
     */
    public int src;
    /**
     * 参数
     */
    public byte[] params = new byte[10];

    /**
     * 当前连接的设备
     */
    public DeviceInfo deviceInfo;

    public NotificationInfo() {
    }

    public NotificationInfo(Parcel in) {
        this.opcode = in.readInt();
        this.src = in.readInt();
        in.readByteArray(this.params);
        Object ret = in.readValue(DeviceInfo.class.getClassLoader());
        if (ret != null) {
            this.deviceInfo = (DeviceInfo) ret;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.opcode);
        dest.writeInt(this.src);
        dest.writeByteArray(this.params);

        if (this.deviceInfo != null) {
            dest.writeValue(this.deviceInfo);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
