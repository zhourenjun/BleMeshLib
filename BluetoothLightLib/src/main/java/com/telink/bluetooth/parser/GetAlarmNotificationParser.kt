package com.telink.bluetooth.parser

import com.telink.bluetooth.mode.NotificationInfo
import com.telink.bluetooth.param.Opcode
import com.telink.util.NumberUtils

/**
 * 闹铃通知解析器
 */
class GetAlarmNotificationParser private constructor() : NotificationParser<GetAlarmNotificationParser.AlarmInfo>() {

    override fun opcode()= Opcode.BLE_GATT_OP_CTRL_E7.getValue()

    override fun parse(notifyInfo: NotificationInfo): AlarmInfo? {

        val params = notifyInfo.params
        var offset = 8
        val total = params[offset].toInt() and 0xFF

        if (total == 0)
            return null

        offset = 1
        val index = params[offset++].toInt() and 0xFF
        val data = (params[offset].toInt() and 0xFF).toByte()
        offset = 3
        val sceneId = params[offset].toInt() and 0xFF

        val action = NumberUtils.byteToInt(data, 0, 3)
        val type = NumberUtils.byteToInt(data, 4, 6)
        val status = data.toInt() shr 7 and 0x01
        val time = NumberUtils.bytesToLong(params, 3, 5)

        val alarmInfo = AlarmInfo()
        alarmInfo.index = index
        alarmInfo.total = total
        alarmInfo.action = AlarmAction.valueOf(action)
        alarmInfo.type = AlarmType.valueOf(type)
        alarmInfo.status = AlarmStatus.valueOf(status)
        alarmInfo.time = time
        alarmInfo.sceneId = sceneId

        return alarmInfo
    }

    enum class AlarmAction constructor(val value: Int) {
        OFF(0), ON(1), SCENE(2);
        companion object {
            fun valueOf(value: Int): AlarmAction? {
                val values = AlarmAction.values()
                return values.firstOrNull { value == it.value }
            }
        }
    }

    enum class AlarmType constructor(val value: Int) {
        DAY(0), WEEK(1);
        companion object {
            fun valueOf(value: Int): AlarmType? {
                val values = AlarmType.values()
                return values.firstOrNull { value == it.value }
            }
        }
    }

    enum class AlarmStatus constructor(val value: Int) {
        ENABLE(1), DISABLE(0);
        companion object {
            fun valueOf(value: Int): AlarmStatus? {
                val values = AlarmStatus.values()
                return values.firstOrNull { value == it.value }
            }
        }
    }

    inner class AlarmInfo {
        var index: Int = 0
        var total: Int = 0
        var action: AlarmAction? = null
        var type: AlarmType? = null
        var status: AlarmStatus? = null
        var time: Long = 0
        var sceneId: Int = 0

        val month = if (type == AlarmType.DAY) (time shr 32 and 0x0C).toInt() else 0
        val dayOrWeek = (time shr 24 and 0xFF).toInt()
        val hour = (time shr 16 and 0xFF).toInt()
        val minute = (time shr 8 and 0xFF).toInt()
        val second = (time and 0xFF).toInt()
    }

    companion object {
        fun create() = GetAlarmNotificationParser()
    }
}
