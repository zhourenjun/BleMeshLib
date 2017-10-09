package com.telink.util

import java.nio.charset.Charset

object Strings {

    @JvmOverloads
    fun stringToBytes(str: String, length: Int = 0): ByteArray {

        val srcBytes: ByteArray = str.toByteArray(Charset.defaultCharset())

        if (length <= 0) {
            return str.toByteArray(Charset.defaultCharset())
        }
        val result = ByteArray(length)

        if (srcBytes.size <= length) {
            System.arraycopy(srcBytes, 0, result, 0, srcBytes.size)
        } else {
            System.arraycopy(srcBytes, 0, result, 0, length)
        }

        return result
    }

    fun bytesToString(data: ByteArray?): String? {
        return if (data == null || data.isEmpty()) null else String(data, Charset.defaultCharset()).trim { it <= ' ' }
    }

    fun isEmpty(str: String?): Boolean {
        return str == null || str.trim { it <= ' ' }.isEmpty()
    }
}
