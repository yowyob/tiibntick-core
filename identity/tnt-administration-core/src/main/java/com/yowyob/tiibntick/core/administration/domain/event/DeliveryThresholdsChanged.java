package com.yowyob.tiibntick.core.administration.domain.event;

import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code DeliveryThresholdsChanged} (spec §5.1, annex 9.1 row 26): published on {@code tnt.administration.events}
 * after every change of the platform thresholds or of an organization override, so that readers (delivery-core's
 * threshold cache, lot L2) drop their copy. Carried in the topic's existing body
 * {@code {eventType, module, tenantId, aggregateId, payload, occurredAt}}; this class builds {@code payload}.
 *
 * <p>Payload: {@code scope} ({@code PLATFORM} | {@code ORGANIZATION}), {@code change} ({@code UPDATED} |
 * {@code DELETED}), {@code organizationId} (null for the platform), {@code version}, {@code updatedBy},
 * {@code updatedAt}, {@code thresholds} (field → value, null = inherited / removed), {@code previousThresholds}
 * (same shape, null on creation), and for the platform {@code independentRelayStorageMinDays} /
 * {@code independentRelayStorageMaxDays}.
 */
public record DeliveryThresholdsChanged(
        Scope scope,
        Change change,
        UUID organizationId,
        long version,
        UUID updatedBy,
        Instant updatedAt,
        DeliveryThresholdValues thresholds,
        DeliveryThresholdValues previousThresholds,
        RelayStorageBounds relayBounds) {

    public static final String EVENT_TYPE = "DeliveryThresholdsChanged";
    public static final String MODULE = "administration";

    /** Fixed aggregate id of the platform setting (the setting has a single row, {@code id = 'PLATFORM'}). */
    public static final UUID PLATFORM_AGGREGATE_ID =
            UUID.nameUUIDFromBytes("tnt.administration.delivery-thresholds.PLATFORM".getBytes(StandardCharsets.UTF_8));

    public enum Scope { PLATFORM, ORGANIZATION }

    public enum Change { UPDATED, DELETED }

    public UUID aggregateId() {
        return scope == Scope.PLATFORM ? PLATFORM_AGGREGATE_ID : organizationId;
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", scope.name());
        payload.put("change", change.name());
        payload.put("organizationId", organizationId == null ? null : organizationId.toString());
        payload.put("version", version);
        payload.put("updatedBy", updatedBy == null ? null : updatedBy.toString());
        payload.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        payload.put("thresholds", thresholds == null ? null : thresholds.toFieldMap());
        payload.put("previousThresholds", previousThresholds == null ? null : previousThresholds.toFieldMap());
        if (scope == Scope.PLATFORM) {
            payload.put("independentRelayStorageMinDays", relayBounds == null ? null : relayBounds.minDays());
            payload.put("independentRelayStorageMaxDays", relayBounds == null ? null : relayBounds.maxDays());
        }
        return payload;
    }
}
