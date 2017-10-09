package com.telink.util

import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


object BleLog {
    private var logEnabled = true
    private var tag = "zrj"
    private var isSaveLog = false
    private val ROOT = Environment.getExternalStorageDirectory().path!!// SD卡中的根目录
    private val PATH = "/zrj/log"
    private var PATH_LOG_INFO = ROOT + PATH

    fun v(msg: String, customTag: String = tag) {
        log(Log.VERBOSE, customTag, msg)
    }

    fun d(msg: String, customTag: String = tag) {
        log(Log.DEBUG, customTag, msg)
    }

    fun i(msg: String, customTag: String = tag) {
        log(Log.INFO, customTag, msg)
    }

    fun w(msg: String, customTag: String = tag) {
        log(Log.WARN, customTag, msg)
    }

    fun e(msg: String, customTag: String = tag) {
        log(Log.ERROR, customTag, msg)
    }

    fun json(msg: String, customTag: String = tag) {
        val json = formatJson(msg)
        log(Log.ERROR, customTag, json)
    }

    /**
     * 格式化json
     */
    private fun formatJson(json: String): String {
        return try {
            val trimJson = json.trim()
            when {
                trimJson.startsWith("{") -> JSONObject(trimJson).toString(4)
                trimJson.startsWith("[") -> JSONArray(trimJson).toString(4)
                else -> trimJson
            }
        } catch (e: JSONException) {
            e.printStackTrace().toString()
        }
    }

    /**
     * 输出日志
     * @param priority 日志级别
     */
    private fun log(priority: Int, customTag: String, msg: String) {
        if (!logEnabled) return
        val elements = Thread.currentThread().stackTrace
        val index = findIndex(elements)
        val element = elements[index]
        val tag = handleTag(element, customTag)
        var content = "(${element.fileName}:${element.lineNumber}).${element.methodName}:  $msg"
        Log.println(priority, tag, content)
        if (isSaveLog) {
            point(PATH_LOG_INFO, tag, content)
        }
    }


    /**
     * 处理tag逻辑
     */
    private fun handleTag(element: StackTraceElement, customTag: String): String = when {
        customTag.isNotBlank() -> customTag
        else -> element.className.substringAfterLast(".")
    }

    /**
     * 寻找当前调用类在[elements]中的下标
     */
    private fun findIndex(elements: Array<StackTraceElement>): Int {
        var index = 5
        while (index < elements.size) {
            val className = elements[index].className
            if (className != BleLog::class.java.name && !elements[index].methodName.startsWith("log")) {
                return index
            }
            index++
        }
        return -1
    }

    private fun point(path: String?, tag: String, msg: String) {
        var path = path
        if (isSDAva) {
            val date = Date()
            val dateFormat = SimpleDateFormat("",
                    Locale.SIMPLIFIED_CHINESE)
            dateFormat.applyPattern("yyyy")
            path = path + dateFormat.format(date) + "/"
            dateFormat.applyPattern("MM")
            path += dateFormat.format(date) + "/"
            dateFormat.applyPattern("dd")
            path += dateFormat.format(date) + ".log"
            dateFormat.applyPattern("[yyyy-MM-dd HH:mm:ss]")
            val time = dateFormat.format(date)
            val file = File(path)
            if (!file.exists())
                createDipPath(path)
            var out: BufferedWriter? = null
            try {
                out = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true)))
                out.write("$time $tag $msg\r\n")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    if (out != null) {
                        out.close()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
    }

    /**
     * 根据文件路径 递归创建文件
     */
    private fun createDipPath(file: String) {
        val parentFile = file.substring(0, file.lastIndexOf("/"))
        val file1 = File(file)
        val parent = File(parentFile)
        if (!file1.exists()) {
            parent.mkdirs()
            try {
                file1.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    private val isSDAva: Boolean
        get() =  Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED || Environment.getExternalStorageDirectory().exists()

}