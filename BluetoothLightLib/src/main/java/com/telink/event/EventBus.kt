package com.telink.event

import android.os.Handler
import android.os.Looper
import com.telink.util.BleLog
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

open class EventBus<T> {

    private val mEventListeners: MutableMap<T, List<EventListener<T>>> = ConcurrentHashMap()
    private val mEventQueue: Queue<Event<T>> = ConcurrentLinkedQueue()
    private val mCurrentThreadHandler = Handler(Looper.myLooper())
    private val mMainThreadHandler = Handler(Looper.getMainLooper())
    private val mLock = Any()
    private var processing = false
    private val task = Runnable { processEvent() }

    fun addEventListener(eventType: T, listener: EventListener<T>) {

        synchronized(mEventListeners) {
            val listeners: MutableList<EventListener<T>>

            if (mEventListeners.containsKey(eventType)) {
                listeners = mEventListeners[eventType] as MutableList<EventListener<T>>
            } else {
                listeners = ArrayList()
                mEventListeners.put(eventType, listeners)
            }

            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeEventListener(listener: EventListener<T>) {
        synchronized(mEventListeners) {
            for (eventType in mEventListeners.keys) {
                removeEventListener(eventType, listener)
            }
        }
    }

    fun removeEventListener(eventType: T, listener: EventListener<T>) {
        synchronized(mEventListeners) {
            if (mEventListeners.containsKey(eventType)) {
                val listeners =  mEventListeners[eventType] as MutableList<EventListener<T>>
                listeners.remove(listener)
            }
        }
    }

    fun removeEventListeners() {
        synchronized(mEventListeners) {
            for (eventType in mEventListeners.keys) {
                val listeners = mEventListeners[eventType] as MutableList<EventListener<T>>
                listeners.clear()
                mEventListeners.remove(eventType)
            }
        }
    }

    open fun dispatchEvent(event: Event<T>) {
        mEventQueue.add(event)
        BleLog.d("postCommand event : " + event.type + "--" + event.javaClass.name)
        synchronized(mLock) {
            if (!processing)
                processOnThread()
        }
    }

    private fun processOnThread() {
        var event: Event<T>? = null
        synchronized(mEventQueue) {
            event = mEventQueue.peek()
            if (event == null)
                return
        }
        when (event?.threadMode) {
            Event.ThreadMode.Background -> EXECUTOR_SERVICE.execute(task)
            Event.ThreadMode.Main -> mMainThreadHandler.post(task)
            Event.ThreadMode.Default -> mCurrentThreadHandler.post(task)
        }
    }

    private fun processEvent() {
        var event: Event<T>? = null
        synchronized(mEventQueue) {
            event = mEventQueue.poll()
            if (event == null)
                return
        }
        val eventType = event?.type
        var listeners: List<EventListener<T>>? = null

        synchronized(mEventListeners) {
            if (mEventListeners.containsKey(eventType)) {
                listeners = mEventListeners[eventType]
            }
        }

        if ( !listeners!!.isEmpty()) {
            synchronized(mLock) {
                processing = true
            }
            for (listener in listeners!!) {
                listener?.performed(event!!)
            }
        }
        processEventCompleted()
    }

    private fun processEventCompleted() {
        synchronized(mLock) {
            processing = false
        }
        if (!mEventQueue.isEmpty())
            processOnThread()
    }

    private class DefaultThreadFactory internal constructor() : ThreadFactory {
        private val threadNumber = AtomicInteger(1)
        private val group: ThreadGroup
        private val namePrefix: String

        init {
            val s = System.getSecurityManager()
            group = if (s != null)
                s.threadGroup
            else
                Thread.currentThread().threadGroup
            namePrefix = "pool-${POOL_NUMBER.getAndIncrement()}-thread-"
        }

        override fun newThread(r: Runnable): Thread {

            val run = Runnable {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                r.run()
            }

            val thread = Thread(group, run,namePrefix + threadNumber.getAndIncrement(),0)
            if (thread.isDaemon)
                thread.isDaemon = false
            if (thread.priority != Thread.NORM_PRIORITY)
                thread.priority = Thread.NORM_PRIORITY

            return thread
        }

        companion object {
            private val POOL_NUMBER = AtomicInteger(1)
        }
    }

    companion object {

        private val CPU_COUNT = Runtime.getRuntime().availableProcessors()
        private val CORE_POOL_SIZE = CPU_COUNT + 1
        private val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
        private val KEEP_ALIVE = 1
        private val sPoolWorkQueue = LinkedBlockingQueue<Runnable>(128)
        private val sThreadFactory = DefaultThreadFactory()

        private val EXECUTOR_SERVICE = ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE.toLong(),
                TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory)
    }
}
