package com.yowyob.tiibntick.core.marketback.domain.model;

import java.util.UUID;

/**
 * Value Object — Strongly-typed identifier for Campaign.
 * @author MANFOUO Braun
 */
public record CampaignId(UUID value) {

    public static CampaignId generate() {
        return new CampaignId(UUID.randomUUID());
    }

    public static CampaignId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("CampaignId value must not be null");
        return new CampaignId(value);
    }

    public static CampaignId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
