/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.util;

public final class NumberUtils {

    private NumberUtils() {
    }

    static public int byteToInt(byte s, int bitStartPosition, int bitEndPosition) {
        int bit = bitEndPosition - bitStartPosition + 1;
        int maxValue = 1 << bit;
        int result = 0;

        for (int i = bitEndPosition, j = bit; i > bitStartPosition; i--, j--) {
            result += (s >> i & 0x01) << j;
        }

        return result & maxValue;
    }

    static public long bytesToLong(byte[] s, int start, int length) {
        int end = start + length;
        int max = length - 1;
        long result = 0;

        for (int i = start, j = max; i < end; i++, j--) {
            result += (s[i] & 0xFF) << (8 * j);
        }

        return result;
    }
}
