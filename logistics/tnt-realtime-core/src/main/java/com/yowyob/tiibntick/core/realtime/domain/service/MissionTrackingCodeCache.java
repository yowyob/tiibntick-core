package com.yowyob.tiibntick.core.realtime.domain.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory missionId → trackingCode lookup, populated from
 * {@code tnt-delivery-core}'s {@code MissionStatusChangedEvent} (consumed by
 * {@code MissionStatusEventConsumer}) and read by {@code KalmanEtaUpdaterAdapter}
 * when a GPS ping needs to resolve the tracking code for its mission.
 *
 * <p>GPS pings only carry a {@code missionId}, not a tracking code, so this cache
 * bridges the two without requiring realtime-core to call back into delivery-core.
 * Same per-instance, in-memory tradeoff as {@link GpsPingProcessor}'s own position
 * cache — acceptable for a single-instance deployment, would need a shared store
 * (e.g. Redis) behind a horizontally-scaled one.
 *
 * @author MANFOUO Braun
 */
@Component
public class MissionTrackingCodeCache {

    private final ConcurrentMap<String, String> trackingCodesByMissionId = new ConcurrentHashMap<>();

    public void put(String missionId, String trackingCode) {
        if (missionId == null || missionId.isBlank() || trackingCode == null || trackingCode.isBlank()) {
            return;
        }
        trackingCodesByMissionId.put(missionId, trackingCode);
    }

    public String get(String missionId) {
        return missionId != null ? trackingCodesByMissionId.get(missionId) : null;
    }
}
