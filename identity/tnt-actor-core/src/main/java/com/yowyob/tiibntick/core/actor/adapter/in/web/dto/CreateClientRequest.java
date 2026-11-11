package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

/**
 * A client profile carries no creation-time fields of its own ({@code tenantId}/{@code actorId} come
 * from the authenticated caller). The only optional field is the language the person picked while
 * creating the profile; it becomes the language of the whole account.
 *
 * @param preferredLanguage {@code fr} or {@code en} (regional tags are folded), may be {@code null}
 */
public record CreateClientRequest(String preferredLanguage) {
}
