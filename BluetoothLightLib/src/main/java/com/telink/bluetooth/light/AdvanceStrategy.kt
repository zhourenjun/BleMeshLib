package com.telink.bluetooth.light

import android.os.Handler
import com.telink.util.BleLog
import kotlin.experimental.and

/**
 * 命令写入FIFO策略
 */
abstract class AdvanceStrategy {
    lateinit var mCallback: Callback
    //设置采样率,单位毫秒
    var sampleRate = 320

    var sampleOpcodes: ByteArray
        get() = if (sampleOpcodes == null) DEFAULT_SAMPLE_LIST else sampleOpcodes
        set(value) {
            this.sampleOpcodes = value
        }

    //回调接口,采样到的命令交由回调接口处理
    fun setCallback(mCallback: Callback) {
        this.mCallback = mCallback
    }

    /**
     * 处理传进来的命令
     * @param opcode     命令吗
     * @param address    目标地址
     * @param params     命令参数
     * @param delay      命令延时
     * @param tag        命令标签
     * @param noResponse 命令发送方式
     * @param immediate  是否立即写入底层FIFO
     * @return 命令是否成功写入
     */
    abstract fun postCommand(opcode: Byte, address: Int, params: ByteArray, delay: Int, tag: Any, noResponse: Boolean, immediate: Boolean): Boolean

    //启动,执行初始化
    abstract fun onStart()

    //停止，做一些清理工作
    abstract fun onStop()

    interface Callback {
        fun onCommandSampled(opcode: Byte, address: Int, params: ByteArray?, tag: Any?, delay: Int): Boolean
    }

    /**
     * 默认的命令发送策略
     */
    private class DefaultAdvanceStrategy : AdvanceStrategy() {
        private var lastSampleTime = 0L
        // 上一个是否是采样指令
        private val commandSender: Handler = Handler()
        // 上一次发送指令时间
        private var lastCmdTime = 0L

        private val task: StrategyTask

        private inner class StrategyTask : Runnable {
            private var opcode: Byte = 0
            private var address: Int = 0
            private var params: ByteArray? = null
            private var delay: Int = 0
            private var tag: Any? = null

            fun setCommandArgs(opcode: Byte, address: Int, params: ByteArray, delay: Int, tag: Any) {
                this.opcode = opcode
                this.address = address
                this.params = params
                this.delay = delay
                this.tag = tag
            }

            override fun run() {
                BleLog.d("Delay run Opcode :${Integer.toHexString(opcode.toInt())}")
                lastSampleTime = System.currentTimeMillis()
                lastCmdTime = System.currentTimeMillis()
                this@DefaultAdvanceStrategy.mCallback.onCommandSampled(opcode, address, params, tag, delay)
            }
        }

        init {
            task = StrategyTask()
        }

        override fun onStart() {
            lastSampleTime = 0
        }

        override fun onStop() {}

        override fun postCommand(opcode: Byte, address: Int, params: ByteArray, delay: Int, tag: Any, noResponse: Boolean, immediate: Boolean): Boolean {
            var delay = delay
            val currentTime = System.currentTimeMillis()
            // 是否直接发送指令
            var now = false
            if (lastCmdTime == 0L) {
                //第一个命令,直接写入FIFO
                now = true
            } else if (immediate) {
                //立即发送的命令
                now = true
            } else {
                if (isExists(opcode, sampleOpcodes)) {
                    val interval = currentTime - lastSampleTime
                    when {
                        interval < 0 -> {
                            now = true
                            lastSampleTime = currentTime
                        }
                        interval >= sampleRate -> {
                            now = true
                            lastSampleTime = currentTime
                        }
                        else -> {
                            commandSender.removeCallbacks(task)
                            task.setCommandArgs(opcode, address, params, delay, tag)
                            commandSender.postDelayed(task, sampleRate - interval)
                        }
                    }
                } else {
                    now = true
                }
            }

            if (now && mCallback != null) {
                BleLog.d("Sample Opcode : ${Integer.toHexString(opcode.toInt())} delay:$delay")

                val period = currentTime - lastCmdTime
                if (period in 1..(COMMAND_DELAY - 1)) {
                    if (delay < COMMAND_DELAY - period)
                        delay = (COMMAND_DELAY - period).toInt()
                }
                lastCmdTime = System.currentTimeMillis()
                //所有采样到的命令立即交给回调接口处理
                return mCallback.onCommandSampled(opcode, address, params, tag, delay)
            }
            BleLog.d("Delay Opcode : ${Integer.toHexString(opcode.toInt())}")
            return false
        }


    }

    companion object {

        val DEFAULT_SAMPLE_LIST = byteArrayOf(0xD0.toByte(), 0xD2.toByte(), 0xE2.toByte())

        private val DEFAULT = DefaultAdvanceStrategy()
        private var definition: AdvanceStrategy? = null

        private val COMMAND_DELAY = 320

        var default: AdvanceStrategy?
            get() {
                synchronized(AdvanceStrategy::class.java) {
                    if (definition != null)
                        return definition
                }
                return DEFAULT
            }
            set(strategy) = synchronized(AdvanceStrategy::class.java) {
                if (strategy != null)
                    definition = strategy
            }

        fun isExists(opcode: Byte, opcodeList: ByteArray): Boolean {
            return opcodeList.any { it and 0xFF.toByte() == opcode and 0xFF.toByte() }
        }
    }
}
