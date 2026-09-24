package com.yowyob.tiibntick.core.realtime.domain.model.enums;

/**
 * Type of device/client connecting via WebSocket.
 * Used to route push notifications and adapt message formats.
 *
 * @author MANFOUO Braun
 */
public enum DeviceType {

    /** Android native app (React Native). */
    ANDROID,

    /** iOS native app (React Native). */
    IOS,

    /** Progressive Web App running in a browser. */
    PWA_BROWSER,

    /** Desktop browser or Electron app. */
    DESKTOP,

    /**
     * Synthetic device type assigned when a presence record is auto-created
     * by an HTTP GPS ping (PATCH /location) rather than a WebSocket CONNECT.
     * The mobile app sent no STOMP handshake, so no real device metadata is available.
     */
    HTTP_POLLING
}
