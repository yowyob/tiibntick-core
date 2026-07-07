package com.yowyob.tiibntick.core.realtime.domain.model;

import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;

/**
 * Device information associated with a WebSocket session.
 * Captures client-side metadata for observability and push notification routing.
 *
 * @author MANFOUO Braun
 */
public record DeviceInfo(
        DeviceType platform,
        String appVersion,
        String osVersion,
        String pushToken
) {

    public static DeviceInfo of(DeviceType platform, String appVersion, String osVersion) {
        return new DeviceInfo(platform, appVersion, osVersion, null);
    }

    public static DeviceInfo of(DeviceType platform, String appVersion, String osVersion, String pushToken) {
        return new DeviceInfo(platform, appVersion, osVersion, pushToken);
    }

    public boolean hasPushToken() {
        return pushToken != null && !pushToken.isBlank();
    }
}
