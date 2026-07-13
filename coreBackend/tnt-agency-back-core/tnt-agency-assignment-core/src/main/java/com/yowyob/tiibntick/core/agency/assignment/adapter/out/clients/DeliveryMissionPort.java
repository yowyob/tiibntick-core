package com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Outbound port — mission creation via Core sales order dispatch. */
public interface DeliveryMissionPort {

    Mono<CreatedCoreMission> createMission(CreateMissionRequest request);

    record CreateMissionRequest(
            UUID tenantId,
            UUID organizationId,
            UUID agencyId,
            UUID coreAgencyId,
            UUID missionId,
            String pickupAddress,
            String deliveryAddress,
            String senderName,
            String recipientName,
            String recipientPhone,
            Double weightKg,
            Instant scheduledAt
    ) {}

    record CreatedCoreMission(UUID coreMissionId, String orderNumber) {}
}
