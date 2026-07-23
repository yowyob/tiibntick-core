package com.yowyob.tiibntick.core.agency.sync.application.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntUnauthorizedException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.AgencyMissionR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.DelivererR2dbcRepository;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validates Agency sync push operations <em>before</em> generic batch execution so path
 * {@code agencyId} cannot be bypassed via forged aggregate/deliverer/hub ids.
 */
@Component
@RequiredArgsConstructor
public class AgencyOfflinePushGuard {

    private final AgencyMissionR2dbcRepository missionRepo;
    private final DelivererR2dbcRepository delivererRepo;
    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final ObjectMapper objectMapper;

    public Mono<Void> validateBeforeBatch(
            UUID tenantId, UUID agencyId, List<Map<String, Object>> operations) {
        if (operations == null || operations.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(operations)
                .concatMap(op -> validateOne(tenantId, agencyId, op))
                .then();
    }

    private Mono<Void> validateOne(UUID tenantId, UUID agencyId, Map<String, Object> op) {
        if (op == null) {
            return Mono.error(new TntValidationException("Offline operation entry must not be null"));
        }
        String typeRaw = stringVal(op, "type");
        OfflineOpType type = AgencyOfflineOpContracts.requireExactType(typeRaw);
        String aggregateType = stringVal(op, "aggregateType");
        String aggregateId = stringVal(op, "aggregateId");
        String payloadJson = payloadAsString(op.get("payload"));

        if (AgencyOfflineOpContracts.isGpsType(type)) {
            AgencyOfflineOpContracts.requireGpsAggregate(aggregateType, aggregateId);
            AgencyOfflineOpContracts.ParsedGpsPayload parsed;
            try {
                parsed = AgencyOfflineOpContracts.parseAndValidateGpsPayload(payloadJson, objectMapper);
            } catch (TntValidationException e) {
                return Mono.error(e);
            }
            UUID aggregateDelivererId = AgencyOfflineOpContracts.parseUuid(aggregateId, "aggregateId");
            if (!aggregateDelivererId.equals(parsed.delivererId())) {
                return Mono.error(new TntValidationException(
                        "GPS aggregateId must equal payload.delivererId"));
            }
            return assertDelivererOwnership(tenantId, agencyId, parsed.delivererId())
                    .then(Mono.defer(() -> assertMissionOwnershipIfPresent(
                            tenantId, agencyId, parsed.missionId())));
        }

        AgencyOfflineOpContracts.requireMissionAggregate(aggregateType, aggregateId);
        AgencyOfflineOpContracts.ParsedMissionPayload parsed;
        try {
            parsed = AgencyOfflineOpContracts.parseAndValidatePayload(type, payloadJson, objectMapper);
        } catch (TntValidationException e) {
            return Mono.error(e);
        }

        UUID missionId = AgencyOfflineOpContracts.parseUuid(aggregateId, "aggregateId");
        return assertMissionOwnership(tenantId, agencyId, missionId)
                .then(Mono.defer(() -> assertDelivererOwnership(tenantId, agencyId, parsed.delivererId())))
                .then(Mono.defer(() -> assertHubOwnershipIfPresent(tenantId, agencyId, parsed.hubId())));
    }

    private Mono<Void> assertMissionOwnership(UUID tenantId, UUID agencyId, UUID missionId) {
        return missionRepo.findByIdAndTenantId(missionId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "MISSION_NOT_FOUND", "Mission introuvable: " + missionId)))
                .flatMap(mission -> {
                    if (!agencyId.equals(mission.getAgencyId())) {
                        return Mono.error(new TntUnauthorizedException(
                                "AGENCY_SCOPE_VIOLATION",
                                "Mission " + missionId + " does not belong to agency " + agencyId));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> assertMissionOwnershipIfPresent(UUID tenantId, UUID agencyId, UUID missionId) {
        if (missionId == null) {
            return Mono.empty();
        }
        return assertMissionOwnership(tenantId, agencyId, missionId);
    }

    private Mono<Void> assertDelivererOwnership(UUID tenantId, UUID agencyId, UUID delivererId) {
        return delivererRepo.findByIdAndTenantId(delivererId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "DELIVERER_NOT_FOUND", "Livreur introuvable: " + delivererId)))
                .flatMap(deliverer -> {
                    if (!agencyId.equals(deliverer.getAgencyId())) {
                        return Mono.error(new TntUnauthorizedException(
                                "AGENCY_SCOPE_VIOLATION",
                                "Deliverer " + delivererId + " does not belong to agency " + agencyId));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> assertHubOwnershipIfPresent(UUID tenantId, UUID agencyId, UUID hubId) {
        if (hubId == null) {
            return Mono.empty();
        }
        return hubRepo.findByIdAndTenantId(hubId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Hub introuvable: " + hubId)))
                .flatMap(hub -> {
                    if (!agencyId.equals(hub.getAgencyId())) {
                        return Mono.error(new TntUnauthorizedException(
                                "AGENCY_SCOPE_VIOLATION",
                                "Hub " + hubId + " does not belong to agency " + agencyId));
                    }
                    return Mono.empty();
                });
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
            throw new TntValidationException("Unable to serialize offline payload: " + e.getMessage());
        }
    }

    private static String stringVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
