package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

/**
 * Empty body: a client profile carries no creation-time fields of its own
 * ({@code tenantId}/{@code actorId} come from the authenticated caller).
 */
public record CreateClientRequest() {
}
