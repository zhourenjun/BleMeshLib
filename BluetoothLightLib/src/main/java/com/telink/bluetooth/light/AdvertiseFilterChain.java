/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 广播过滤器链
 */
public final class AdvertiseFilterChain {

    private static final AdvertiseFilterChain DEFAULT_CHAIN = new AdvertiseFilterChain("Telink default filter chain");

    static {
        DEFAULT_CHAIN.add(DefaultAdvertiseDataFilter.create());
    }

    private String name;
    private List<AdvertiseDataFilter> mFilters;

    private AdvertiseFilterChain(String name) {
        this.name = name;
        this.mFilters = new ArrayList<>();
    }

    public static AdvertiseFilterChain getDefault() {
        return DEFAULT_CHAIN;
    }

    public String getName() {
        return name;
    }

    public AdvertiseFilterChain add(AdvertiseDataFilter filter) {
        synchronized (this) {
            this.mFilters.add(filter);
        }
        return this;
    }

    public AdvertiseFilterChain remove(AdvertiseDataFilter filter) {
        synchronized (this) {
            if (this.mFilters.contains(filter)) {
                this.mFilters.remove(filter);
            }
        }
        return this;
    }

    public AdvertiseFilterChain removeAll() {
        synchronized (this) {
            this.mFilters.clear();
        }
        return this;
    }

    public Iterator<AdvertiseDataFilter> iterator() {
        synchronized (this) {
            return this.mFilters.iterator();
        }
    }
}
