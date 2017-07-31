/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth;

import com.telink.util.Arrays;

import java.util.UUID;

public class Command {

    public UUID serviceUUID;
    public UUID characteristicUUID;
    public CommandType type;
    public byte[] data;
    public Object tag;
    public int delay;

    public Command() {
        this(null, null, CommandType.WRITE);
    }

    public Command(UUID serviceUUID, UUID characteristicUUID, CommandType type) {
        this(serviceUUID, characteristicUUID, type, null);
    }

    public Command(UUID serviceUUID, UUID characteristicUUID, CommandType type,
                   byte[] data) {
        this(serviceUUID, characteristicUUID, type, data, null);
    }

    public Command(UUID serviceUUID, UUID characteristicUUID, CommandType type,
                   byte[] data, Object tag) {

        this.serviceUUID = serviceUUID;
        this.characteristicUUID = characteristicUUID;
        this.type = type;
        this.data = data;
        this.tag = tag;
    }

    public static Command newInstance() {
        return new Command();
    }

    public void clear() {
        this.serviceUUID = null;
        this.characteristicUUID = null;
        this.data = null;
    }

    @Override
    public String toString() {
        String d = "";

        if (data != null)
            d = Arrays.bytesToHexString(this.data, ",");

        return "{ tag : " + this.tag + ", type : " + this.type
                + " characteristicUUID :" + characteristicUUID.toString() + " data: " + d + " delay :" + delay + "}";
    }

    public enum CommandType {
        READ, WRITE, WRITE_NO_RESPONSE, ENABLE_NOTIFY, DISABLE_NOTIFY
    }

    public interface Callback {

        void success(Peripheral peripheral, Command command, Object obj);

        void error(Peripheral peripheral, Command command, String errorMsg);

        boolean timeout(Peripheral peripheral, Command command);
    }
}
