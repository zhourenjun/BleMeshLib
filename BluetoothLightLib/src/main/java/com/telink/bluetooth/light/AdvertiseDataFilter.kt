package com.telink.bluetooth.light

import android.bluetooth.BluetoothDevice

/**
 * 广播包过滤接口
 */
interface AdvertiseDataFilter<out E : LightPeripheral> {

    /**
     * 过滤接口
     * @param device     扫描到的蓝牙设备
     * @param rssi       信号强度
     * @param scanRecord 广播数据包
     */
    fun filter(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray): E?
}
