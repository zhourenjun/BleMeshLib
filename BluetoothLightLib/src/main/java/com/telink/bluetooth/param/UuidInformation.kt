package com.telink.bluetooth.param

import java.util.*

enum class UuidInformation constructor(val value: UUID, val info: String) {

    TELINK_SERVICE(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1910"), "Telink SmartLight Service"),
    TELINK_CHARACTERISTIC_PAIR(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1914"), "pair"),
    TELINK_CHARACTERISTIC_COMMAND(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1912"), "command"),
    TELINK_CHARACTERISTIC_NOTIFY(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1911"), "notify"),
    TELINK_CHARACTERISTIC_OTA(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1913"), "ota"),

    SERVICE_DEVICE_INFORMATION(UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), "Device Information Service"),
    CHARACTERISTIC_FIRMWARE(UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb"), "Firmware Revision"),
    CHARACTERISTIC_MANUFACTURER(UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb"), "Manufacturer Name"),
    CHARACTERISTIC_MODEL(UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb"), "Model Number"),
    CHARACTERISTIC_HARDWARE(UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb"), "Hardware Revision")
}
