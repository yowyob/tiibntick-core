package com.yowyob.tiibntick.core.agency.sync.adapter.in.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntUnauthorizedException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.assignment.adapter.in.web.dto.MissionResponse;
import com.yowyob.tiibntick.core.agency.assignment.application.service.MissionService;
import com.yowyob.tiibntick.core.agency.sync.application.offline.AgencyOfflineOpContracts;
import com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyDelivererService;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationApplier;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Agency {@link IOfflineOperationApplier} for aggregate {@code MISSION}.
 * Dispatches to {@link MissionService} for pickup, delivery, hub deposit and anomaly.
 *
 * <p>Path-level agency ownership is enforced earlier by {@code AgencyOfflinePushGuard}.
 * This applier additionally ensures the deliverer matches the mission assignment when set.</p>
 *
 * <p>Idempotency relies on stable client operation ids and
 * {@code OfflineQueueDomainService.isAlreadyApplied} before re-invocation.</p>
 *
 * <p>GPS updates are handled by {@link AgencyGpsOfflineOperationApplier}.</p>
 */
@Component
public class AgencyMissionOfflineOperationApplier implements IOfflineOperationApplier {

    private static final Logger log = LoggerFactory.getLogger(AgencyMissionOfflineOperationApplier.class);

    private final MissionService missionService;
    private final AgencyDelivererService delivererService;
    private final ObjectMapper objectMapper;

    public AgencyMissionOfflineOperationApplier(
            MissionService missionService,
            AgencyDelivererService delivererService,
            ObjectMapper objectMapper) {
        this.missionService = missionService;
        this.delivererService = delivererService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String aggregateType) {
        return AgencyOfflineOpContracts.AGGREGATE_MISSION.equals(aggregateType);
    }

    @Override
    public Mono<String> apply(OfflineOperation op) {
        return Mono.defer(() -> {
            OfflineOpType type = op.getType();
            if (!AgencyOfflineOpContracts.MISSION_TYPES.contains(type)) {
                return Mono.error(new TntValidationException(
                        "Agency mission applier does not support type: " + type));
            }
            UUID tenantId = AgencyOfflineOpContracts.parseUuid(op.getTenantId(), "tenantId");
            UUID missionId = AgencyOfflineOpContracts.parseUuid(op.getAggregateId(), "aggregateId");
            AgencyOfflineOpContracts.ParsedMissionPayload payload =
                    AgencyOfflineOpContracts.parseAndValidatePayload(type, op.getPayload(), objectMapper);
            validateOptionalPickupAction(type, op.getPayload());

            log.debug("Applying Agency offline {} for mission {} (opId={})",
                    type, missionId, op.getId());

            return assertDelivererAssignment(tenantId, missionId, payload.delivererId())
                    .then(Mono.defer(() -> dispatch(type, tenantId, missionId, payload)))
                    .map(this::toJson);
        });
    }

    private Mono<MissionResponse> dispatch(
            OfflineOpType type,
            UUID tenantId,
            UUID missionId,
            AgencyOfflineOpContracts.ParsedMissionPayload payload) {
        return switch (type) {
            case MISSION_STATUS_UPDATE ->
                    missionService.pickup(tenantId, missionId, payload.delivererId())
                            .map(MissionResponse::from);
            case DELIVERY_CONFIRMATION ->
                    missionService.deliver(
                                    tenantId, missionId, payload.delivererId(), payload.proofOrTracking())
                            .map(MissionResponse::from);
            case HUB_DEPOSIT ->
                    missionService.depositAtHub(
                                    tenantId, missionId, payload.hubId(),
                                    payload.delivererId(), payload.proofOrTracking())
                            .map(MissionResponse::from);
            case ANOMALY_REPORT ->
                    missionService.reportAnomaly(
                                    tenantId, missionId, payload.delivererId(),
                                    payload.anomalyType(),
                                    payload.description() != null ? payload.description() : "",
                                    payload.fatal())
                            .map(MissionResponse::from);
            default -> Mono.error(new TntValidationException("Unsupported Agency offline type: " + type));
        };
    }

    private Mono<Void> assertDelivererAssignment(UUID tenantId, UUID missionId, UUID delivererId) {
        return delivererService.getById(tenantId, delivererId)
                .flatMap(deliverer -> missionService.getById(tenantId, missionId)
                        .flatMap(mission -> {
                            if (!deliverer.agencyId().equals(mission.getAgencyId())) {
                                return Mono.error(new TntUnauthorizedException(
                                        "AGENCY_SCOPE_VIOLATION",
                                        "Deliverer does not belong to the mission agency"));
                            }
                            if (mission.getAssignedDelivererId() != null
                                    && !mission.getAssignedDelivererId().equals(delivererId)) {
                                return Mono.error(new TntUnauthorizedException(
                                        "MISSION_ASSIGNMENT_VIOLATION",
                                        "Deliverer is not assigned to this mission"));
                            }
                            return Mono.empty();
                        }));
    }

    private void validateOptionalPickupAction(OfflineOpType type, String payloadJson) {
        if (type != OfflineOpType.MISSION_STATUS_UPDATE) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode action = root.get("action");
            if (action != null && !action.isNull() && action.isTextual()
                    && !action.asText().isBlank()
                    && !"PICKUP".equalsIgnoreCase(action.asText())) {
                throw new TntValidationException(
                        "MISSION_STATUS_UPDATE action must be PICKUP when present");
            }
        } catch (TntValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new TntValidationException("Invalid offline payload JSON: " + e.getMessage());
        }
    }

    private String toJson(MissionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MissionResponse for offline apply", e);
        }
    }
}
