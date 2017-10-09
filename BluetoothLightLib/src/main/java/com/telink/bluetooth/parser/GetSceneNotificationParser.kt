package com.telink.bluetooth.parser

import com.telink.bluetooth.mode.NotificationInfo
import com.telink.bluetooth.param.Opcode

class GetSceneNotificationParser private constructor() : NotificationParser<GetSceneNotificationParser.SceneInfo>() {

    override fun opcode() = Opcode.BLE_GATT_OP_CTRL_C1.getValue()

    override fun parse(notifyInfo: NotificationInfo): SceneInfo? {

        val params = notifyInfo.params
        var offset = 8
        val total = params[offset].toInt()

        if (total == 0)
            return null

        offset = 0
        val index = params[offset++].toInt() and 0xFF
        val lum = params[offset++].toInt() and 0xFF
        val r = params[offset++].toInt() and 0xFF
        val g = params[offset++].toInt() and 0xFF
        val b = params[offset].toInt() and 0xFF

        val sceneInfo = SceneInfo()
        sceneInfo.index = index
        sceneInfo.total = total
        sceneInfo.lum = lum
        sceneInfo.rgb = (r shl 16) + (g shl 8) + b

        return sceneInfo
    }

    inner class SceneInfo {
        var index: Int = 0
        var total: Int = 0
        var lum: Int = 0
        var rgb: Int = 0
    }

    companion object {
        fun create() = GetSceneNotificationParser()
    }
}
