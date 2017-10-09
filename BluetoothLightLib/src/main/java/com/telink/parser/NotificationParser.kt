package com.telink.parser

import android.util.SparseArray
import com.telink.mode.NotificationInfo
import com.telink.param.Opcode

/**
 * Notification解析器接口
 * 继承NotificationParser编写自定义的解析器,通过[NotificationParser.register]来注册.
 */
abstract class NotificationParser<out E> {

    //操作码
    abstract fun opcode(): Byte

    // 将[NotificationInfo.params]转换成自定义的数据格式
    abstract fun parse(notifyInfo: NotificationInfo): E?

    companion object {
        private val PARSER_ARRAY = SparseArray<NotificationParser<*>>()

        //注册解析器
        fun register(parser: NotificationParser<*>) {
            synchronized(this) {
                PARSER_ARRAY.put(parser.opcode().toInt() and 0xFF, parser)
            }
        }

        //获取解析器
        operator fun get(opcode: Int): NotificationParser<*> {
            synchronized(this) {
                return PARSER_ARRAY.get(opcode and 0xFF)
            }
        }

        operator fun get(opcode: Opcode): NotificationParser<*> {
            return get(opcode.getValue().toInt())
        }
    }
}
