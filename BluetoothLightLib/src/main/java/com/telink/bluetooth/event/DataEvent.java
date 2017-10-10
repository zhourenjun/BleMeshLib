/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.event;

import com.telink.util.Event;

public class DataEvent<A> extends Event<String> {

    protected A args;

    public DataEvent(Object sender, String type, A args) {
        super(sender, type);
        this.args = args;
    }

    public A getArgs() {
        return args;
    }
}
