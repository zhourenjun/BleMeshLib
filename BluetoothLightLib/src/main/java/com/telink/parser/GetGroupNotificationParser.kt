package com.telink.parser

import com.telink.mode.NotificationInfo
import com.telink.param.Opcode
import java.util.*

/**
 * 分组通知解析器
 */
class GetGroupNotificationParser private constructor() : NotificationParser<List<Int>>() {

    override fun opcode() = Opcode.BLE_GATT_OP_CTRL_D4.getValue()

    override fun parse(notifyInfo: NotificationInfo): List<Int> {

        val mAddress = ArrayList<Int>()
        val params = notifyInfo.params
        val length = params.size
        var position = 0
        var address: Int

        while (position < length) {
            address = params[position++].toInt()
            address = address and 0xFF
            if (address == 0xFF)
                break
            address = address or 0x8000
            mAddress.add(address)
        }
        return mAddress
    }

    companion object {
        fun create() = GetGroupNotificationParser()
    }
}
