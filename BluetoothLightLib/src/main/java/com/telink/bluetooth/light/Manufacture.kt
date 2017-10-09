package com.telink.bluetooth.light

import com.telink.bluetooth.param.UuidInformation
import java.util.*

/**
 * 厂商信息设置接口
 */
class Manufacture private constructor(
        val name: String,   //厂商名称
        val version: String,  //版本信息
        val info: String,    // 描述信息
        val factoryName: String,  //设备的出厂名
        val factoryPassword: String,  //设备的出厂密码
        defaultLongTermKey: ByteArray,
        val vendorId: Int,   // 厂商Id
        val otaDelay: Int,  //OTA数据包写入间隔时间 可以根据不同的手机设置此参数.
        val otaSize: Int,
        serviceUUID: UUID,
        pairUUID: UUID,
        commandUUID: UUID,
        notifyUUID: UUID,
        otaUUID: UUID) {

    private val uuidMap = HashMap<String, UUID>()
    //设备的出厂LTK
    val factoryLtk: ByteArray

    init {
        factoryLtk = Arrays.copyOf(defaultLongTermKey, 16)
        putUUID(UUIDType.SERVICE.key, serviceUUID)
        putUUID(UUIDType.PAIR.key, pairUUID)
        putUUID(UUIDType.COMMAND.key, commandUUID)
        putUUID(UUIDType.OTA.key, otaUUID)
        putUUID(UUIDType.NOTIFY.key, notifyUUID)
    }

    fun getUUID(uuidType: UUIDType)= getUUID(uuidType.key)

    private fun getUUID(key: String): UUID? {
        var result: UUID? = null
        synchronized(uuidMap) {
            if (uuidMap.containsKey(key))
                result = uuidMap[key]
        }
        return result
    }

    private fun putUUID(key: String, value: UUID) {
        synchronized(uuidMap) {
            if (!uuidMap.containsKey(key))
                uuidMap.put(key, value)
        }
    }

    enum class UUIDType  constructor(val key: String) {

        SERVICE("SERVICE_UUID"), PAIR("PAIR_UUID"), COMMAND("COMMAND_UUID"), OTA("OTA_UUID"), NOTIFY("NOTIFY_UUID")
    }

    class Builder {
        private var name = "telink"
        private var version = "1.0"
        private var info = "TELINK SEMICONDUCTOR (Shanghai) CO, LTD is a fabless IC design company"
        private var factoryName = "longhorn"
        private var factoryPassword = "123456"
        private var factoryLtk = byteArrayOf(0xC0.toByte(), 0xC1.toByte(), 0xC2.toByte(), 0xC3.toByte(),
                0xC4.toByte(), 0xC5.toByte(), 0xC6.toByte(), 0xC7.toByte(), 0xD8.toByte(), 0xD9.toByte(),
                0xDA.toByte(), 0xDB.toByte(), 0xDC.toByte(), 0xDD.toByte(), 0xDE.toByte(), 0xDF.toByte())
        private var vendorId = 0x1102
        private var serviceUUID = UuidInformation.TELINK_SERVICE.value
        private var pairUUID = UuidInformation.TELINK_CHARACTERISTIC_PAIR.value
        private var commandUUID = UuidInformation.TELINK_CHARACTERISTIC_COMMAND.value
        private var notifyUUID = UuidInformation.TELINK_CHARACTERISTIC_NOTIFY.value
        private var otaUUID = UuidInformation.TELINK_CHARACTERISTIC_OTA.value
        private var otaDelay = 0
        private var otaSize = 128

        //设置厂商名称
        fun setName(name: String): Builder {
            this.name = name
            return this
        }
        // 设置版本信息
        fun setVersion(version: String): Builder {
            this.version = version
            return this
        }

        //设置厂商描述信息
        fun setInfo(info: String): Builder {
            this.info = info
            return this
        }

        //设置出厂名
        fun setFactoryName(factoryName: String): Builder {
            this.factoryName = factoryName
            return this
        }

        // 设置出厂密码
        fun setFactoryPassword(factoryPassword: String): Builder {
            this.factoryPassword = factoryPassword
            return this
        }

        //设置出厂LTK
        fun setFactoryLtk(factoryLtk: ByteArray): Builder {
            this.factoryLtk = factoryLtk
            return this
        }

        //设置厂商标识
        fun setVendorId(vendorId: Int): Builder {
            this.vendorId = vendorId
            return this
        }

        fun setOtaDelay(otaDelay: Int): Builder {
            this.otaDelay = otaDelay
            return this
        }

        fun setOtaSize(otaSize: Int): Builder {
            this.otaSize = otaSize
            return this
        }

        //设置设备的ServiceUUID
        fun setServiceUUID(serviceUUID: UUID): Builder {
            this.serviceUUID = serviceUUID
            return this
        }

        //设置配对用的UUID
        fun setPairUUID(pairUUID: UUID): Builder {
            this.pairUUID = pairUUID
            return this
        }

        //设置发送命令用的UUID
        fun setCommandUUID(commandUUID: UUID): Builder {
            this.commandUUID = commandUUID
            return this
        }

        //设置Notification用的UUID
        fun setNotifyUUID(notifyUUID: UUID): Builder {
            this.notifyUUID = notifyUUID
            return this
        }

        // 设置OTA用的UUID
        fun setOtaUUID(otaUUID: UUID): Builder {
            this.otaUUID = otaUUID
            return this
        }

        fun build(): Manufacture = Manufacture(name, version, info, factoryName, factoryPassword,
                factoryLtk, vendorId, otaDelay, otaSize, serviceUUID, pairUUID, commandUUID,
                notifyUUID, otaUUID)
    }

    companion object {

        // 获取默认厂商,即Telink相关描述
        private val defaultManufacture = Builder().build()

        private var definitionManufacture: Manufacture? = null

        //设置自定义厂商
        fun setManufacture(manufacture: Manufacture) {
            synchronized(this) {
                definitionManufacture = manufacture
            }
        }

        //获取当前的厂商,如果不设置自定义厂商则为默认厂商
        val default: Manufacture
            get() {
                synchronized(this) {
                    if (definitionManufacture == null)
                        return defaultManufacture
                }

                return definitionManufacture!!
            }
    }
}
