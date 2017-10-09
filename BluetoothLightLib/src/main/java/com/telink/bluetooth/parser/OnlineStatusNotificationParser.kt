package com.telink.bluetooth.parser

import com.telink.bluetooth.light.ConnectionStatus
import com.telink.bluetooth.mode.NotificationInfo
import com.telink.bluetooth.param.Opcode
import java.util.*

class OnlineStatusNotificationParser private constructor() : NotificationParser<List<OnlineStatusNotificationParser.DeviceNotificationInfo>>() {

    override fun opcode()= Opcode.BLE_GATT_OP_CTRL_DC.getValue()

    override fun parse(notifyInfo: NotificationInfo): List<DeviceNotificationInfo>? {

        val params = notifyInfo.params

        var meshAddress: Int
        var status: Int
        var brightness: Int
        var reserve: Int

        var position = 0
        val packetSize = 4
        val length = params.size

        var notificationInfoList: MutableList<DeviceNotificationInfo>? = null
        var deviceNotifyInfo: DeviceNotificationInfo

        while (position + packetSize < length) {

            meshAddress = params[position++].toInt()
            status = params[position++].toInt()
            brightness = params[position++].toInt()
            reserve = params[position++].toInt()

            meshAddress = meshAddress and 0xFF

            if (meshAddress == 0x00 || meshAddress == 0xFF && brightness == 0xFF)
                break

            if (notificationInfoList == null)
                notificationInfoList = ArrayList()

            deviceNotifyInfo = DeviceNotificationInfo()
            deviceNotifyInfo.meshAddress = meshAddress
            deviceNotifyInfo.brightness = brightness
            deviceNotifyInfo.reserve = reserve
            deviceNotifyInfo.status = status

            when {
                status == 0 -> deviceNotifyInfo.connectionStatus = ConnectionStatus.OFFLINE
                brightness != 0 -> deviceNotifyInfo.connectionStatus = ConnectionStatus.ON
                else -> deviceNotifyInfo.connectionStatus = ConnectionStatus.OFF
            }

            notificationInfoList.add(deviceNotifyInfo)
        }

        return notificationInfoList
    }

    inner class DeviceNotificationInfo {
        var meshAddress: Int = 0
        var status: Int = 0
        var brightness: Int = 0
        var reserve: Int = 0
        var connectionStatus = ConnectionStatus.OFFLINE
    }

    companion object {
        fun create()= OnlineStatusNotificationParser()
    }
}
