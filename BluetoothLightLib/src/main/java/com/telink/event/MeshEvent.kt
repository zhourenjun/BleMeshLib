package com.telink.event

/**
 * 网络事件
 */
class MeshEvent(sender: Any, type: String, args: Int) : DataEvent<Int>(sender, type, args) {
    companion object {
        val UPDATE_COMPLETED = "event_update_completed"
        //连接到不任何设备的时候分发此事件
        val OFFLINE = "event_offline"
        //出现异常时分发此事件,比如蓝牙关闭了
        val ERROR = "event_error"

        fun newInstance(sender: Any, type: String, args: Int): MeshEvent {
            return MeshEvent(sender, type, args)
        }
    }
}
