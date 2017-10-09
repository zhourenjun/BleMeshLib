package com.telink.light

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.telink.param.UuidInformation
import com.telink.util.Strings
import java.util.*

class LightPeripheral(device: BluetoothDevice, scanRecord: ByteArray, rssi: Int,
                      meshName: ByteArray, meshAddress: Int) : Peripheral(device, scanRecord, rssi) {

    private val advProperties: MutableMap<String, Any> = HashMap()
    private val characteristicsValue: MutableMap<UUID, ByteArray> = HashMap()

    var meshChanged: Boolean = false

    private var meshName: ByteArray? = null
    var password: ByteArray? = null
    var longTermKey: ByteArray? = null
    var meshAddress: Int = 0
        set(value) {
            field = value
            newMeshAddress = value
        }

    private lateinit var mCallback: Callback

    var meshNameStr: String? = null
        private set
    var newMeshAddress = -1
    var retry = 0
        private set

    val meshUUID: Int
        get() = getAdvPropertyAsInt(ADV_MESH_UUID)

    val productUUID: Int
        get() = getAdvPropertyAsInt(ADV_PRODUCT_UUID)

    val status: Int
        get() = getAdvPropertyAsInt(ADV_STATUS)

    val firmwareRevision: String?
        get() {
            val characteristicUUID = UuidInformation.CHARACTERISTIC_FIRMWARE.value
            return getCharacteristicValueAsString(characteristicUUID)
        }

    init {
        setMeshName(meshName)
        this.meshAddress = meshAddress
    }

    fun getMeshName() = meshName

    fun setMeshName(value: String) {
        meshNameStr = value
    }

     fun setMeshName(value: ByteArray) {
        meshNameStr = Strings.bytesToString(value)
        meshName = value
    }

    fun putAdvProperty(key: String, value: Any) {
        advProperties.put(key, value)
    }

    fun getAdvProperty(key: String) = advProperties[key]

    fun getAdvPropertyAsString(key: String) = advProperties[key] as String

    fun addRetry() {
        retry++
    }

    private fun getAdvPropertyAsInt(key: String) = advProperties[key] as Int

    fun getAdvPropertyAsLong(key: String) = advProperties[key] as Long

    fun getAdvPropertyAsBytes(key: String) = advProperties[key] as ByteArray

    fun putCharacteristicValue(characteristicUUID: UUID, value: ByteArray) {
        characteristicsValue.put(characteristicUUID, value)
    }

    private fun getCharacteristicValue(characteristicUUID: UUID): ByteArray? {
        return if (characteristicsValue.containsKey(characteristicUUID)) characteristicsValue[characteristicUUID] else null
    }

    private fun getCharacteristicValueAsString(characteristicUUID: UUID): String? {
        val value = getCharacteristicValue(characteristicUUID)
        return if (value != null) String(value) else null
    }

    fun connect(context: Context, callback: Callback) {
        mCallback = callback
        super.connect(context)
    }

    override fun disconnect() {
        super.disconnect()
    }

    private fun findService(serviceUUID: UUID): BluetoothGattService? {

        if (services == null || services.isEmpty())
            return null

        return services.firstOrNull { it.uuid == serviceUUID }
    }

    fun findCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {

        val mService = findService(serviceUUID) ?: return null

        val characteristics = mService.characteristics

        return characteristics.firstOrNull { it.uuid == characteristicUUID }
    }

    override fun onConnect() {
        super.onConnect()

        if (mCallback != null)
            mCallback.onConnect(this)
    }

    override fun onDisconnect() {
        super.onDisconnect()
        if (mCallback != null) {
            mCallback.onDisconnect(this)
        }
    }

    override fun onServicesDiscovered(services: List<BluetoothGattService>) {
        super.onServicesDiscovered(services)

        if (mCallback != null)
            mCallback.onServicesDiscovered(this, services)
    }

    override fun onNotify(data: ByteArray, serviceUUID: UUID?, characteristicUUID: UUID?, tag: Any?) {
        super.onNotify(data, serviceUUID, characteristicUUID, tag)

        if (mCallback != null)
            mCallback.onNotify(this, data, serviceUUID, characteristicUUID, tag)
    }

    override fun onRssiChanged() {
        super.onRssiChanged()

        if (mCallback != null)
            mCallback.onRssiChanged(this)
    }

    interface Callback {
        fun onConnect(light: LightPeripheral)

        fun onDisconnect(light: LightPeripheral)

        fun onServicesDiscovered(light: LightPeripheral, services: List<BluetoothGattService>)

        fun onNotify(light: LightPeripheral, data: ByteArray, serviceUUID: UUID?, characteristicUUID: UUID?, tag: Any?)

        fun onRssiChanged(light: LightPeripheral)
    }

    companion object {
        val ADV_MESH_NAME = "adv_mesh_name"
        val ADV_MESH_ADDRESS = "adv_mesh_address"
        val ADV_MESH_UUID = "adv_mesh_uuid"
        val ADV_PRODUCT_UUID = "adv_product_uuid"
        val ADV_STATUS = "adv_status"
    }
}
