package com.yowyob.tiibntick.core.realtime.adapter.in.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Metadata carried in the STOMP CONNECT frame body (optional JSON body).
 * The JWT token is extracted from the {@code Authorization} header of the
 * HTTP handshake request, not from here.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectRequest(
        String deviceType,
        String appVersion,
        String osVersion,
        String pushToken
) {}
