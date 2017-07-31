/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.bluetooth.light;

public final class GetSceneNotificationParser extends NotificationParser<GetSceneNotificationParser.SceneInfo> {

    private GetSceneNotificationParser() {
    }

    public static GetSceneNotificationParser create() {
        return new GetSceneNotificationParser();
    }

    @Override
    public byte opcode() {
        return Opcode.BLE_GATT_OP_CTRL_C1.getValue();
    }

    @Override
    public SceneInfo parse(NotificationInfo notifyInfo) {

        byte[] params = notifyInfo.params;
        int offset = 8;
        int total = params[offset];

        if (total == 0)
            return null;

        offset = 0;
        int index = params[offset++] & 0xFF;
        int lum = params[offset++] & 0xFF;
        int r = params[offset++] & 0xFF;
        int g = params[offset++] & 0xFF;
        int b = params[offset] & 0xFF;

        SceneInfo sceneInfo = new SceneInfo();
        sceneInfo.index = index;
        sceneInfo.total = total;
        sceneInfo.lum = lum;
        sceneInfo.rgb = (r << 16) + (g << 8) + b;

        return sceneInfo;
    }

    public final class SceneInfo {
        public int index;
        public int total;
        public int lum;
        public int rgb;
    }
}
