package com.telink.light

import android.bluetooth.BluetoothDevice
import com.telink.util.Arrays
import com.telink.util.BleLog
import kotlin.experimental.and

/**
 * 默认的广播过滤器
 *
 * 根据VendorId识别设备.
 */
class DefaultAdvertiseDataFilter private constructor() : AdvertiseDataFilter<LightPeripheral> {

    override fun filter(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray): LightPeripheral? {
        BleLog.w(device.name + "-->" + Arrays.bytesToHexString(scanRecord, ":"))
        val length = scanRecord.size
        var packetPosition = 0
        var packetContentLength: Int
        var packetSize: Int
        var position: Int
        var type: Int
        var meshName: ByteArray? = null
        var rspData = 0

        while (packetPosition < length) {
            packetSize = scanRecord[packetPosition].toInt()
            if (packetSize == 0)
                break
            position = packetPosition + 1
            type = (scanRecord[position] and 0xFF.toByte()).toInt()
            position++
            if (type == 0x09) {
                packetContentLength = packetSize - 1
                if (packetContentLength > 16 || packetContentLength <= 0)
                    return null
                meshName = ByteArray(16)
                System.arraycopy(scanRecord, position, meshName, 0, packetContentLength)
            } else if (type == 0xFF) {
                rspData++
                if (rspData == 2) {
                    val vendorId = (scanRecord[position++].toInt() shl 8) + scanRecord[position++]
                    if (vendorId != Manufacture.default.vendorId)
                        return null
                    val meshUUID = scanRecord[position++] + (scanRecord[position++].toInt() shl 8)
                    position += 4
                    val productUUID = scanRecord[position++] + (scanRecord[position++].toInt() shl 8)
                    val status = scanRecord[position++] and 0xFF.toByte()
                    val meshAddress = scanRecord[position++] + (scanRecord[position].toInt() shl 8)
                    val light = LightPeripheral(device, scanRecord, rssi, meshName!!, meshAddress)
                    light.putAdvProperty(LightPeripheral.ADV_MESH_NAME, meshName)
                    light.putAdvProperty(LightPeripheral.ADV_MESH_ADDRESS, meshAddress)
                    light.putAdvProperty(LightPeripheral.ADV_MESH_UUID, meshUUID)
                    light.putAdvProperty(LightPeripheral.ADV_PRODUCT_UUID, productUUID)
                    light.putAdvProperty(LightPeripheral.ADV_STATUS, status)
                    return light
                }
            }
            packetPosition += packetSize + 1
        }
        return null
    }

    companion object {
        fun create(): DefaultAdvertiseDataFilter = DefaultAdvertiseDataFilter()
    }
}
