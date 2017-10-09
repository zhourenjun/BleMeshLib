package com.telink.bluetooth.parser

import com.telink.bluetooth.mode.NotificationInfo
import com.telink.bluetooth.param.Opcode
import java.util.*

/**
 * 时间同步通知解析器
 */
class GetTimeNotificationParser private constructor() : NotificationParser<Calendar>() {

    override fun opcode()= Opcode.BLE_GATT_OP_CTRL_E9.getValue()

    override fun parse(notifyInfo: NotificationInfo): Calendar {
        val params = notifyInfo.params
        var offset = 0
        val year = (params[offset++].toInt() and (0xFF shl 8)) + params[offset++]
        val month = params[offset++].toInt() and 0xFF
        val day = params[offset++].toInt() and 0xFF
        val hour = params[offset++].toInt() and 0xFF
        val minute = params[offset++].toInt() and 0xFF
        val second = params[offset].toInt() and 0xFF
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, hour, minute, second)
        return calendar
    }

    companion object {
        fun create()= GetTimeNotificationParser()
    }
}
