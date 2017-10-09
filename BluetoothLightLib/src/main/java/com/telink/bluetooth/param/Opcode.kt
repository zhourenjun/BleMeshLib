package com.telink.bluetooth.param

enum class Opcode constructor(private val value: Byte, val info: String) {

    /**
     * Mesh Pairing Opcode
     */
    BLE_GATT_OP_PAIR_REQ(0x01.toByte(), ""),
    BLE_GATT_OP_PAIR_RSP(0x02.toByte(), ""),
    BLE_GATT_OP_PAIR_REJECT(0x03.toByte(), ""),
    BLE_GATT_OP_PAIR_NETWORK_NAME(0x04.toByte(), ""),
    BLE_GATT_OP_PAIR_PASS(0x05.toByte(), ""),
    BLE_GATT_OP_PAIR_LTK(0x06.toByte(), ""),
    BLE_GATT_OP_PAIR_CONFIRM(0x07.toByte(), ""),
    BLE_GATT_OP_PAIR_LTK_REQ(0x08.toByte(), ""),
    BLE_GATT_OP_PAIR_LTK_RSP(0x09.toByte(), ""),
    BLE_GATT_OP_PAIR_DELETE(0x0A.toByte(), ""),
    BLE_GATT_OP_PAIR_DEL_RSP(0x0B.toByte(), ""),
    BLE_GATT_OP_PAIR_ENC_REQ(0x0C.toByte(), ""),
    BLE_GATT_OP_PAIR_ENC_RSP(0x0D.toByte(), ""),
    BLE_GATT_OP_PAIR_ENC_FAIL(0x0E.toByte(), ""),
    /**
     * Mesh Control Opcode
     */
    BLE_GATT_OP_CTRL_D0(0xD0.toByte(), "on off"),
    BLE_GATT_OP_CTRL_D1(0xD1.toByte(), "reserved"),
    BLE_GATT_OP_CTRL_D2(0xD2.toByte(), "set brightness and color"),
    BLE_GATT_OP_CTRL_D3(0xD3.toByte(), "reserved"),
    BLE_GATT_OP_CTRL_D4(0xD4.toByte(), "response to short group id query"),
    BLE_GATT_OP_CTRL_D5(0xD5.toByte(), "response to long group id query"),
    BLE_GATT_OP_CTRL_D6(0xD6.toByte(), "response to long group id query"),
    BLE_GATT_OP_CTRL_D7(0xD7.toByte(), "add group or remove group"),
    BLE_GATT_OP_CTRL_D8(0xD8.toByte(), "reserved"),
    BLE_GATT_OP_CTRL_D9(0xD9.toByte(), "reserved"),
    BLE_GATT_OP_CTRL_DA(0xDA.toByte(), "status query"),
    BLE_GATT_OP_CTRL_DB(0xDB.toByte(), "status response"),
    BLE_GATT_OP_CTRL_DC(0xDC.toByte(), "online status report"),
    BLE_GATT_OP_CTRL_DD(0xDD.toByte(), "group id query"),
    BLE_GATT_OP_CTRL_DE(0xDE.toByte(), "reserved"),
    BLE_GATT_OP_CTRL_DF(0xDF.toByte(), "reserved"),
    BLE_GATT_OP_CTRL_E0(0xE0.toByte(), "set device address"),
    BLE_GATT_OP_CTRL_E1(0xE1.toByte(), "notify the device address information"),
    BLE_GATT_OP_CTRL_E2(0xE2.toByte(), "configure rgb value"),
    BLE_GATT_OP_CTRL_E3(0xE3.toByte(), "kick out / reset factory"),
    BLE_GATT_OP_CTRL_E4(0xE4.toByte(), "set device time"),
    BLE_GATT_OP_CTRL_E5(0xE5.toByte(), "alarm operation opcode"),
    BLE_GATT_OP_CTRL_E6(0xE6.toByte(), "get device alarm"),
    BLE_GATT_OP_CTRL_E7(0xE7.toByte(), "alarm response"),
    BLE_GATT_OP_CTRL_E8(0xE8.toByte(), "get device time"),
    BLE_GATT_OP_CTRL_E9(0xE9.toByte(), "time response"),
    BLE_GATT_OP_CTRL_EA(0xEA.toByte(), "user all"),
    BLE_GATT_OP_CTRL_EB(0xEB.toByte(), "user all notify"),
    BLE_GATT_OP_CTRL_EE(0xEE.toByte(), "scene operation opcode"),
    BLE_GATT_OP_CTRL_EF(0xEF.toByte(), "load scene opcode"),
    BLE_GATT_OP_CTRL_C0(0xC0.toByte(), "get scene opcode"),
    BLE_GATT_OP_CTRL_C1(0xC1.toByte(), "scene response"),
    BLE_GATT_OP_CTRL_C8(0xC8.toByte(), "get version");

    fun getValue() = (value.toInt() and 0xFF).toByte()

    companion object {

        fun valueOf(value: Byte): Opcode? {
            var v = (value.toInt() and 0xFF).toByte()
            for (opcode in Opcode.values()) {
                var opcodeValue = opcode.getValue()
                if (opcodeValue == v)
                    return opcode
            }
            return null
        }
    }
}
