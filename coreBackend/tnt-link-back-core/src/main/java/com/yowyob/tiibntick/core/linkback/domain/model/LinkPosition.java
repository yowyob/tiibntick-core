package com.yowyob.tiibntick.core.linkback.domain.model;

import java.time.Instant;

/**
 * Last-known position of a tracked mission/deliverer, materialized from
 * {@code tnt.realtime.gps.position.updated} events (Chantier G, Audit n5 P-17).
 *
 * @param missionId  correlates to {@code Delivery.getId()} — this codebase's "mission" is the
 *                    delivery being tracked (see {@code TntTopics.DELIVERY_MISSION_*} naming)
 * @param latitude   last known latitude
 * @param longitude  last known longitude
 * @param occurredAt when this position was reported by the deliverer's device — used to reject
 *                    out-of-order events without depending on Kafka partition ordering
 */
public record LinkPosition(String missionId, double latitude, double longitude, Instant occurredAt) {
}
