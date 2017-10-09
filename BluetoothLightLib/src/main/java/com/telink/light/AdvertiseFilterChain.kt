package com.telink.light

import java.util.*

/**
 * 广播过滤器链
 */
class AdvertiseFilterChain private constructor(val name: String) {
    private val mFilters: MutableList<AdvertiseDataFilter<*>>

    init {
        mFilters = ArrayList()
    }

    fun add(filter: AdvertiseDataFilter<*>): AdvertiseFilterChain {
        synchronized(this) {
            mFilters.add(filter)
        }
        return this
    }

    fun remove(filter: AdvertiseDataFilter<*>): AdvertiseFilterChain {
        synchronized(this) {
            if (mFilters.contains(filter)) {
                mFilters.remove(filter)
            }
        }
        return this
    }

    fun removeAll(): AdvertiseFilterChain {
        synchronized(this) {
            mFilters.clear()
        }
        return this
    }

    operator fun iterator(): Iterator<AdvertiseDataFilter<*>> {
        synchronized(this) {
            return mFilters.iterator()
        }
    }

    companion object {
         val default = AdvertiseFilterChain("Telink default filter chain")
        init {
            default.add(DefaultAdvertiseDataFilter.create())
        }
    }
}
