package com.telink.bluetooth.event

open class DataEvent<A>(sender: Any, type: String, args: A?) : Event<String>(sender, type) {

    var args: A
        protected set

    init {
        this.args = args!!
    }
}
