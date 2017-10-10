/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.util;

import android.os.Build;

public final class BuildUtils {

    private BuildUtils() {
    }

    public static int assetSdkVersion(String version) {

        String[] v1 = version.split("\\.");
        String[] v2 = Build.VERSION.RELEASE.split("\\.");

        int len1 = v1.length;
        int len2 = v2.length;

        int len = len1 > len2 ? len2 : len1;

        int tempV1;
        int tempV2;

        for (int i = 0; i < len; i++) {
            tempV1 = Integer.parseInt(v1[i]);
            tempV2 = Integer.parseInt(v2[i]);

            if (tempV2 < tempV1)
                return -1;
            else if (tempV2 > tempV1)
                return 1;
        }

        return 0;
    }
}
