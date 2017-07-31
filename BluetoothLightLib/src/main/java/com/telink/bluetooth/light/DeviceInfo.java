/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 设备信息类
 */
public class DeviceInfo implements Parcelable {

    public static final Creator<DeviceInfo> CREATOR = new Creator<DeviceInfo>() {
        @Override
        public DeviceInfo createFromParcel(Parcel in) {
            return new DeviceInfo(in);
        }

        @Override
        public DeviceInfo[] newArray(int size) {
            return new DeviceInfo[size];
        }
    };

    /**
     * Mac地址
     */
    public String macAddress;
    /**
     * 设备名称
     */
    public String deviceName;

    /**
     * 网络名称
     */
    public String meshName;
    /**
     * 网络地址
     */
    public int meshAddress;
    public int meshUUID;
    /**
     * 设备的产品标识符
     */
    public int productUUID;
    public int status;
    public byte[] longTermKey = new byte[16];
    /**
     * 设备的firmware版本
     */
    public String firmwareRevision;

    public DeviceInfo() {
    }

    public DeviceInfo(Parcel in) {
        this.macAddress = in.readString();
        this.deviceName = in.readString();
        this.meshName = in.readString();
        this.firmwareRevision = in.readString();
        this.meshAddress = in.readInt();
        this.meshUUID = in.readInt();
        this.productUUID = in.readInt();
        this.status = in.readInt();
        in.readByteArray(this.longTermKey);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.macAddress);
        dest.writeString(this.deviceName);
        dest.writeString(this.meshName);
        dest.writeString(this.firmwareRevision);
        dest.writeInt(this.meshAddress);
        dest.writeInt(this.meshUUID);
        dest.writeInt(this.productUUID);
        dest.writeInt(this.status);
        dest.writeByteArray(this.longTermKey);
    }
}
