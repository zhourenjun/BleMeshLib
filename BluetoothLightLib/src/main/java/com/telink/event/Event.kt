package com.telink.event

open class Event<T> @JvmOverloads constructor(sender: Any?, type: T?, threadMode: ThreadMode = ThreadMode.Default) {

    var sender: Any
        protected set
    var type: T
        protected set
     var threadMode = ThreadMode.Default

    init {
        this.sender = sender!!
        this.type = type!!
        this.threadMode = threadMode
    }


    fun setThreadMode(mode: ThreadMode): Event<T> {
        threadMode = mode
        return this
    }

    enum class ThreadMode {
        Background, Main, Default
    }
}