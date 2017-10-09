package com.telink.util

import java.util.*

/**
 * 数组工具类
 */
object Arrays {

    /**
     * 反转byte数组
     */
    fun reverse(a: ByteArray?): ByteArray? {
        if (a == null)
            return null
        var p1 = 0
        var p2 = a.size
        val result = ByteArray(p2)
        while (--p2 >= 0) {
            result[p2] = a[p1++]
        }
        return result
    }

    /**
     * 反转byte数组中的某一段
     */
    fun reverse(arr: ByteArray, begin: Int, end: Int): ByteArray {
        var begin = begin
        var end = end
        while (begin < end) {
            val temp = arr[end]
            arr[end] = arr[begin]
            arr[begin] = temp
            begin++
            end--
        }
        return arr
    }

    /**
     * 比较两个byte数组中的每一项值是否相等
     */
    fun equals(array1: ByteArray?, array2: ByteArray?): Boolean {

        if (array1 == array2) {
            return true
        }
        if (array1 == null || array2 == null || array1.size != array2.size) {
            return false
        }
        return array1.indices.none { array1[it] != array2[it] }
    }

    fun bytesToString(array: ByteArray?): String {

        if (array == null) {
            return "null"
        }
        if (array.isEmpty()) {
            return "[]"
        }

        val sb = StringBuilder(array.size * 6)
        sb.append('[')
        sb.append(array[0].toInt())
        for (i in 1 until array.size) {
            sb.append(", ")
            sb.append(array[i].toInt())
        }
        sb.append(']')
        return sb.toString()
    }

    /**
     * byte数组转成十六进制字符串
     *
     * @param array     原数组
     * @param separator 分隔符
     * @return
     */
    fun bytesToHexString(array: ByteArray?, separator: String): String {

        if (array == null || array.isEmpty())
            return ""
        val sb = StringBuilder()
        val formatter = Formatter(sb)
        formatter.format("%02X", array[0])

        for (i in 1 until array.size) {
            if (!Strings.isEmpty(separator))
                sb.append(separator)
            formatter.format("%02X", array[i])
        }

        formatter.flush()
        formatter.close()
        return sb.toString()
    }

    fun hexToBytes(hexStr: String): ByteArray {
        val length = hexStr.length / 2
        val result = ByteArray(length)
        for (i in 0 until length) {
            result[i] = Integer.parseInt(hexStr.substring(i * 2, i * 2 + 2), 16).toByte()
        }
        return result
    }
}
