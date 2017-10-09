package com.telink.bluetooth.event

import android.app.Application
import com.telink.bluetooth.mode.NotificationInfo
import com.telink.bluetooth.param.Opcode
import com.telink.bluetooth.parser.NotificationParser
import java.util.*
import kotlin.experimental.and

/**
 * 通知事件,比如设备的状态/分组信息发生变化等
 */
class NotificationEvent(sender: Any, type: String, args: NotificationInfo) : DataEvent<NotificationInfo>(sender, type, args) {

    //操作码
    private var opcode = 0
    //源地址,即设备/组地址
    private var src = 0
    init {
        opcode = args.opcode
        src = args.src
    }

    fun parse(): Any? {
        val parser = NotificationParser[opcode]
        return parser?.parse(args)
    }

    companion object {
        val ONLINE_STATUS = "event_online_status"    //设备的状态变化事件
        val GET_GROUP = "event_get_group"            //分组事件
        val GET_ALARM = "event_get_alarm"            //闹铃事件
        val GET_SCENE = "event_get_scene"            // 场景事件
        val GET_TIME = "event_get_time"              // 时间同步事件
        val USER_ALL_NOTIFY = "user_all_notify"      //EB
        val USER_ALL = "user_all"                    //EA
        val GET_DEVICE = "get_device"                //E1
        val GET_DEVICE_STATE = "get_device_state"    //获取设备版本号
        //获取MeshOTA上报的进度,与 GET_DEVICE_STATE 是同一个opCode返回
        val GET_MESH_OTA_PROGRESS = "get_mesh_ota_progress"
        // GET_DEVICE_STATE 返回数据的第一个字节
        val DATA_GET_VERSION = 0x00                  // 获取版本号
        val DATA_GET_MESH_OTA_PROGRESS = 0x04        // 获取进度
        val DATA_GET_OTA_STATE = 0x05                // 获取OTA状态信息

        /**
         * READ_ST_IDLE = 0,    // 没有处于mesh ota状态
         * READ_ST_SLAVE = 1,       // 处于正在接收mesh ota firmware的状态，并且会保存该firmware数据
         * READ_ST_MASTER = 2,    // 处于正在发送mesh ota firmware的状态
         * READ_ST_ONLY_RELAY = 3,    //处于正在接收mesh ota firmware的状态，但是不会保存该firmware数据(比如版本号不符合等)，字节把该数据relay出去。
         */
        val OTA_STATE_IDLE = 0
        val OTA_STATE_SLAVE = 1
        val OTA_STATE_MASTER = 2
        val OTA_STATE_ONLY_RELAY = 3


        private val EVENT_MAPPING = HashMap<Byte, String>()

        init {
            register(Opcode.BLE_GATT_OP_CTRL_DC, ONLINE_STATUS)
            register(Opcode.BLE_GATT_OP_CTRL_D4, GET_GROUP)
            register(Opcode.BLE_GATT_OP_CTRL_E7, GET_ALARM)
            register(Opcode.BLE_GATT_OP_CTRL_E9, GET_TIME)
            register(Opcode.BLE_GATT_OP_CTRL_C1, GET_SCENE)
            register(Opcode.BLE_GATT_OP_CTRL_C8, GET_DEVICE_STATE)
            register(Opcode.BLE_GATT_OP_CTRL_EB, USER_ALL_NOTIFY)
            register(Opcode.BLE_GATT_OP_CTRL_EA, USER_ALL)
            register(Opcode.BLE_GATT_OP_CTRL_E1, GET_DEVICE)
        }

        /**
         * 注册事件类型
         */
        private fun register(opcode: Byte, eventType: String): Boolean {
            var opcode = opcode
            opcode = opcode and 0xFF.toByte()
            synchronized(NotificationEvent::class.java) {
                if (EVENT_MAPPING.containsKey(opcode))
                    return false
                EVENT_MAPPING.put(opcode, eventType)
                return true
            }
        }

        /**
         * 注册事件类型
         */
        private fun register(opcode: Opcode, eventType: String): Boolean {
            return register(opcode.getValue(), eventType)
        }

        /**
         * 获取事件类型
         */
        fun getEventType(opcode: Byte): String? {
            var opcode = opcode
            opcode = opcode and 0xFF.toByte()
            synchronized(NotificationEvent::class.java) {
                if (EVENT_MAPPING.containsKey(opcode))
                    return EVENT_MAPPING[opcode]
            }
            return null
        }

        fun getEventType(opcode: Opcode): String? {
            return getEventType(opcode.getValue())
        }

        fun newInstance(sender: Application, type: String, args: NotificationInfo): NotificationEvent {
            return NotificationEvent(sender, type, args)
        }
    }
}
