package com.telink.util

import java.security.SecureRandom
import java.util.*

object MeshUtils {

    private val GROUP_ADDRESS_MIN = 0x8001
    private val GROUP_ADDRESS_MAX = 0x80FF
    private val DEVICE_ADDRESS_MIN = 0x00C8
    private val DEVICE_ADDRESS_MAX = 0x00FF
    private val CHARS = "123456789aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ+-*/<>/?!@#$%^&;'[]{}|,."

    private val MESH_ADDRESS_COMPARATOR = MeshAddressComparator()

    private var GroupAddress = GROUP_ADDRESS_MIN
    private var DeviceAddress = DEVICE_ADDRESS_MIN

    private lateinit var rng: SecureRandom

    fun generateRandom(length: Int): ByteArray {
        val data = ByteArray(length)
        synchronized(MeshUtils::class.java) {
            if (rng == null) {
                rng = SecureRandom()
            }
        }
        rng.nextBytes(data)
        return data
    }

    fun generateChars(length: Int): ByteArray {
        val charLen = CHARS.length - 1
        var charAt: Int
        val data = ByteArray(length)
        for (i in 0 until length) {
            charAt = Math.round(Math.random() * charLen).toInt()
            data[i] = CHARS[charAt].toByte()
        }
        return data
    }

    @Synchronized
    fun allocGroupAddress(allocAddress: List<Int>?): Int {

        if (allocAddress == null || allocAddress.isEmpty()) {
            GroupAddress = GROUP_ADDRESS_MIN
            return if (GroupAddress + 1 > GROUP_ADDRESS_MAX) -1 else GroupAddress++
        }

        val count = allocAddress.size
        if (count > GROUP_ADDRESS_MAX - GROUP_ADDRESS_MIN)
            return -1
        Collections.sort(allocAddress, MESH_ADDRESS_COMPARATOR)
        val last = allocAddress[count - 1]
        if (last + 1 <= GROUP_ADDRESS_MAX)
            return last + 1
        var prev: Int? = null
        var next: Int?
        var i = 0
        while (i < count) {
            if (prev == null) {
                prev = allocAddress[i]
                i = 1
                continue
            }
            next = allocAddress[i]
            if (prev + 1 != next && prev != GROUP_ADDRESS_MAX) {
                return prev + 1
            }
            if (i + 1 >= count) {

                return if (next >= GROUP_ADDRESS_MAX) {
                    GroupAddress = GROUP_ADDRESS_MIN
                    GroupAddress++
                } else {
                    next + 1
                }
            }
            prev = next
            i++
        }
        return -1
    }

    @Synchronized
    fun allocDeviceAddress(allocAddress: List<Int>?): Int {

        if (allocAddress == null || allocAddress.isEmpty()) {
            DeviceAddress = DEVICE_ADDRESS_MIN
            return if (DeviceAddress + 1 > DEVICE_ADDRESS_MAX) -1 else DeviceAddress++
        }
        val count = allocAddress.size
        if (count > DEVICE_ADDRESS_MAX - DEVICE_ADDRESS_MIN)
            return -1
        Collections.sort(allocAddress, MESH_ADDRESS_COMPARATOR)
        val last = allocAddress[count - 1]
        if (last + 1 <= DEVICE_ADDRESS_MAX)
            return last + 1
        var prev: Int? = null
        var next: Int?
        var i = 0
        while (i < count) {
            if (prev == null) {
                prev = allocAddress[i]
                i = 1
                continue
            }
            next = allocAddress[i]
            if (prev + 1 != next && prev != DEVICE_ADDRESS_MAX) {
                return prev + 1
            }
            if (i + 1 >= count) {
                return if (next >= DEVICE_ADDRESS_MAX) {
                    DeviceAddress = DEVICE_ADDRESS_MIN
                    DeviceAddress++
                } else {
                    next + 1
                }
            }
            prev = next
            i++
        }
        return -1
    }

    private class MeshAddressComparator : Comparator<Int> {
        override fun compare(lhs: Int, rhs: Int): Int {
            if (lhs > rhs)
                return 1
            return if (lhs < rhs) -1 else 0
        }
    }
}
