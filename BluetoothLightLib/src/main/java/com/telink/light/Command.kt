package com.telink.light

import com.telink.util.Arrays
import java.util.*

class Command @JvmOverloads constructor(var serviceUUID: UUID? = null, var characteristicUUID: UUID? = null, var type: CommandType = CommandType.WRITE,
                                        var data: ByteArray? = null, var tag: Any? = null) {
    var delay = 0

    fun clear() {
        serviceUUID = null
        characteristicUUID = null
        data = null
    }

    override fun toString(): String {
        var d = ""
        if (data != null)
            d = Arrays.bytesToHexString(data, ",")

        return ("{ tag : $tag, type : $type , characteristicUUID :$characteristicUUID ,data: $d . delay :$delay}")
    }

    enum class CommandType {
        READ, WRITE, WRITE_NO_RESPONSE, ENABLE_NOTIFY, DISABLE_NOTIFY
    }

    interface Callback {

        fun success(peripheral: Peripheral, command: Command, obj: Any)

        fun error(peripheral: Peripheral, command: Command, errorMsg: String)

        fun timeout(peripheral: Peripheral, command: Command): Boolean
    }

    companion object {
        fun newInstance() = Command()
    }
}
