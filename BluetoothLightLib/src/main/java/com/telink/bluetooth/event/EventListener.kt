package com.telink.bluetooth.event

interface EventListener<T> {
    fun performed(event: Event<T>)
}