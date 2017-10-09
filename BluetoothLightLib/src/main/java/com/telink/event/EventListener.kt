package com.telink.event

interface EventListener<T> {
    fun performed(event: Event<T>)
}