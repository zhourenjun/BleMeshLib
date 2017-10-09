package com.telink.util

import kotlin.experimental.and


object NumberUtils {

    fun byteToInt(s: Byte, bitStartPosition: Int, bitEndPosition: Int): Int {
        val bit = bitEndPosition - bitStartPosition + 1
        val maxValue = 1 shl bit
        var result = 0

        var i = bitEndPosition
        var j = bit
        while (i > bitStartPosition) {
            var temp = s.toInt() shr i
            result += temp and 0x01 shl j
            i--
            j--
        }

        return result and maxValue
    }

    fun bytesToLong(s: ByteArray, start: Int, length: Int): Long {
        val end = start + length
        val max = length - 1
        var result = 0L

        var i = start
        var j = max
        while (i < end) {
            var temp = (s[i] and 0xFF.toByte()).toInt()
            result += ( temp shl 8 * j).toLong()
            i++
            j--
        }

        return result
    }
}
