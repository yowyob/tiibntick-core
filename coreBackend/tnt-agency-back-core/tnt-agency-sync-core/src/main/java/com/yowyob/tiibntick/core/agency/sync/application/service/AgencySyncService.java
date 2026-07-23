package com.yowyob.tiibntick.core.agency.sync.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import com.yowyob.tiibntick.core.agency.sync.adapter.in.web.dto.SyncPullResult;
import com.yowyob.tiibntick.core.agency.sync.adapter.out.persistence.DeviceRegistrationR2dbcRepository;
import com.yowyob.tiibntick.core.agency.sync.adapter.out.persistence.entity.DeviceRegistrationEntity;
import com.yowyob.tiibntick.core.agency.sync.application.offline.AgencyOfflinePushGuard;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.OfflineOpDto;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPullResponse;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushRequest;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushResponse;
import com.yowyob.tiibntick.core.sync.application.port.in.IComputeDeltaUseCase;
import com.yowyob.tiibntick.core.sync.application.port.in.IProcessSyncBatchUseCase;
import com.yowyob.tiibntick.core.sync.application.port.out.IDuckDbSchemaProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Agency-scoped offline sync — delegates delta engine to {@code tnt-sync-core}.
 */
@Service
@RequiredArgsConstructor
public class AgencySyncService {

    private static final Set<String> DEFAULT_AGENCY_FILTERS = Set.of(
            "MISSION", "PACKAGE", "DELIVERER", "HUB_PARCEL", "VEHICLE", "COMMISSION");

    private final IComputeDeltaUseCase computeDelta;
    private final IProcessSyncBatchUseCase processSyncBatch;
    private final IDuckDbSchemaProvider schemaProvider;
    private final DeviceRegistrationR2dbcRepository deviceRepo;
    private final AgencyRegistryService agencyRegistry;
    private final AgencyOfflinePushGuard offlinePushGuard;
    private final ObjectMapper objectMapper;

    public Mono<SyncPullResult> pull(PullInput input) {
        Set<String> filters = resolveFilters(input.filter());
        return agencyRegistry.getById(input.tenantId(), input.agencyId())
                .then(computeDelta.computeDelta(
                        input.userId().toString(),
                        input.tenantId().toString(),
                        input.deviceId(),
                        input.syncToken(),
                        filters))
                .flatMap(response -> touchRegistration(input, response.newSyncToken())
                        .thenReturn(toPullResult(response)));
    }

    public Mono<SyncPullResult> bootstrap(BootstrapInput input) {
        Set<String> filters = resolveFilters(input.filter());
        return agencyRegistry.getById(input.tenantId(), input.agencyId())
                .then(computeDelta.computeDelta(
                        input.userId().toString(),
                        input.tenantId().toString(),
                        input.deviceId(),
                        null,
                        filters))
                .flatMap(response -> touchRegistration(
                        new PullInput(input.tenantId(), input.agencyId(), input.userId(),
                                input.deviceId(), null, input.filter()),
                        response.newSyncToken())
                        .thenReturn(toPullResult(response)));
    }

    @Transactional
    public Mono<SyncPushResponse> push(PushInput input) {
        return agencyRegistry.getById(input.tenantId(), input.agencyId())
                .then(offlinePushGuard.validateBeforeBatch(
                        input.tenantId(), input.agencyId(), input.operations()))
                .then(Mono.fromCallable(() -> {
                    List<OfflineOpDto> operations = input.operations().stream()
                            .map(this::toOfflineOp)
                            .toList();
                    return new SyncPushRequest(input.syncToken(), operations);
                }))
                .flatMap(request -> processSyncBatch.processSyncBatch(
                        input.userId().toString(),
                        input.tenantId().toString(),
                        input.deviceId(),
                        request))
                .flatMap(response -> touchRegistration(
                        new PullInput(input.tenantId(), input.agencyId(), input.userId(),
                                input.deviceId(), null, null),
                        response.newSyncToken())
                        .thenReturn(response));
    }

    public Mono<String> duckDbSchema(UUID tenantId) {
        return schemaProvider.getDdl(tenantId.toString());
    }

    private Mono<Void> touchRegistration(PullInput input, String syncToken) {
        Instant now = Instant.now();
        return deviceRepo.findByTenantIdAndAgencyIdAndUserIdAndDeviceId(
                        input.tenantId(), input.agencyId(), input.userId(), input.deviceId())
                .flatMap(existing -> {
                    existing.setLastSyncToken(syncToken);
                    existing.setUpdatedAt(now);
                    return deviceRepo.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    DeviceRegistrationEntity entity = new DeviceRegistrationEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setTenantId(input.tenantId());
                    entity.setAgencyId(input.agencyId());
                    entity.setUserId(input.userId());
                    entity.setDeviceId(input.deviceId());
                    entity.setLastSyncToken(syncToken);
                    entity.setRegisteredAt(now);
                    entity.setUpdatedAt(now);
                    return deviceRepo.save(entity);
                }))
                .then();
    }

    private SyncPullResult toPullResult(SyncPullResponse response) {
        List<Map<String, Object>> changes = response.records().stream()
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("aggregateType", r.aggregateType());
                    map.put("aggregateId", r.aggregateId());
                    map.put("operation", r.operation());
                    map.put("payload", r.payload());
                    map.put("serverVersion", r.serverVersion());
                    map.put("serverTimestamp", r.serverTimestamp() != null
                            ? r.serverTimestamp().toString() : null);
                    return map;
                })
                .toList();
        return new SyncPullResult(response.newSyncToken(), changes);
    }

    private OfflineOpDto toOfflineOp(Map<String, Object> op) {
        return new OfflineOpDto(
                stringVal(op, "id"),
                stringVal(op, "type"),
                stringVal(op, "aggregateType"),
                stringVal(op, "aggregateId"),
                payloadAsString(op.get("payload")),
                longVal(op, "localTimestampMs", System.currentTimeMillis()),
                longVal(op, "sequenceNumber", 0L)
        );
    }

    private String payloadAsString(Object payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }

    private static String stringVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static long longVal(Map<String, Object> map, String key, long defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            try {
                return Long.parseLong(v.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Set<String> resolveFilters(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return DEFAULT_AGENCY_FILTERS;
        }
        return new LinkedHashSet<>(requested);
    }

    public record PullInput(
            UUID tenantId, UUID agencyId, UUID userId,
            String deviceId, String syncToken, Set<String> filter) {}

    public record BootstrapInput(
            UUID tenantId, UUID agencyId, UUID userId,
            String deviceId, Set<String> filter) {}

    public record PushInput(
            UUID tenantId, UUID agencyId, UUID userId,
            String deviceId, String syncToken,
            List<Map<String, Object>> operations) {}
}
