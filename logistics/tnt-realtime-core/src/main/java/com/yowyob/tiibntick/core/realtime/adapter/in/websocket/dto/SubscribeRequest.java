package com.yowyob.tiibntick.core.realtime.adapter.in.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Optional payload sent in a STOMP SUBSCRIBE frame body.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscribeRequest(
        String subscriptionId,
        String destination
) {}
