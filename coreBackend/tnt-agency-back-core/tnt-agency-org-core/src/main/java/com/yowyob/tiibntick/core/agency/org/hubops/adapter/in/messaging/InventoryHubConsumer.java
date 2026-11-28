package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRelayHubEntity;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubParcelRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.application.mapper.HubParcelMapper;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubOccupancyService;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelExpiryService;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.HubParcelRecord;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.vo.ParcelStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Parcel custody → Agency hubops: parcel projection + occupancy refresh (lot L8, spec §3.7). Reads
 * {@code tnt.parcel.events} (was {@code tnt.inventory.hub.package.deposited / .pickedup}): a parcel entering
 * {@code AT_RELAY_HUB} is deposited at {@code custody.hubId}; a parcel leaving it is withdrawn from the hub it was in
 * ({@code previousState.custody.hubId}). A parcel not collected at the end of its storage
 * ({@code AT_RELAY_HUB → RETURN_REQUESTED}, {@code returnCause = NOT_COLLECTED_AT_RELAY}: the parcel effect of
 * {@code DeliveryFailed(NOT_COLLECTED_AT_RELAY)}, row D12/P11) stays in the hub waiting for its return and is marked
 * EXPIRED — this replaces the agency's own expiry job (spec §6, lot L8): the storage deadline is the common one
 * (§5.2). Other changes and legacy records are ignored. The work is awaited (lot L7 rule): a failure is retried, then
 * dead-lettered to {@code tnt.dlq}.
 */
@Component
@ConditionalOnProperty(name = "tnt.agency.kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryHubConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryHubConsumer.class);

    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final HubParcelRecordR2dbcRepository parcelRepo;
    private final HubOccupancyService occupancyService;
    private final HubParcelExpiryService expiryService;
    private final ObjectMapper objectMapper;

    public InventoryHubConsumer(AgencyRelayHubR2dbcRepository hubRepo,
                                HubParcelRecordR2dbcRepository parcelRepo,
                                HubOccupancyService occupancyService,
                                HubParcelExpiryService expiryService,
                                ObjectMapper objectMapper) {
        this.hubRepo = hubRepo;
        this.parcelRepo = parcelRepo;
        this.occupancyService = occupancyService;
        this.expiryService = expiryService;
        this.objectMapper = objectMapper;
    }

    static final String AT_RELAY_HUB = "AT_RELAY_HUB";
    static final String RETURN_REQUESTED = "RETURN_REQUESTED";
    static final String NOT_COLLECTED_AT_RELAY = "NOT_COLLECTED_AT_RELAY";

    @KafkaListener(
            topics = com.yowyob.tiibntick.common.kafka.TntTopics.PARCEL_EVENTS,
            groupId = "${tnt.kafka.topics.groups.agency-erp-inventory:tnt-agency-erp-inventory-consumer}",
            containerFactory = "agencyOrgHubKafkaListenerFactory"
    )
    public void onParcelEvent(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        Map<String, Object> projection = projection(objectMapper.readTree(record.value()));
        if (projection != null && Boolean.TRUE.equals(projection.get("expired"))) {
            UUID tenantId = UUID.fromString((String) projection.get("tenantId"));
            com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport.awaitBounded(
                    expiryService.expireDeposited((String) projection.get("trackingCode"), tenantId, Instant.now())
                            .then(Mono.just(true)),
                    java.time.Duration.ofSeconds(30));
        } else if (projection != null) {
            boolean deposited = Boolean.TRUE.equals(projection.get("deposited"));
            String trackingCode = (String) projection.get("trackingCode");
            UUID tenantId = UUID.fromString((String) projection.get("tenantId"));
            com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport.awaitBounded(
                    parcelRepo.findByTrackingCodeAndTenantId(trackingCode, tenantId)
                            .map(HubParcelMapper::toDomain)
                            .flatMap(parcel -> applyProjection(parcel, projection, deposited))
                            .switchIfEmpty(Mono.defer(() -> createProjection(projection, tenantId, trackingCode,
                                    deposited)))
                            .flatMap(parcel -> refreshOccupancy(projection, tenantId).thenReturn(parcel)),
                    java.time.Duration.ofSeconds(30));
        }
        ack.acknowledge();
    }

    /**
     * The hub movement of a parcel event in the former inventory shape ({@code trackingCode}, {@code tenantId},
     * {@code hubId}, {@code packageId}, {@code missionId}, {@code deposited}); null when it is not one.
     */
    static Map<String, Object> projection(com.fasterxml.jackson.databind.JsonNode event) {
        if (!com.yowyob.tiibntick.common.kafka.TntAggregateEvent.isAggregateEvent(event)
                || !event.hasNonNull("tenantId")) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode p = event.path("payload");
        com.fasterxml.jackson.databind.JsonNode previous = event.path("previousState");
        String status = p.path("status").asText(null);
        String before = previous.path("status").asText(null);
        boolean entered = AT_RELAY_HUB.equals(status) && !AT_RELAY_HUB.equals(before);
        boolean left = AT_RELAY_HUB.equals(before) && !AT_RELAY_HUB.equals(status);
        boolean expired = left && RETURN_REQUESTED.equals(status)
                && NOT_COLLECTED_AT_RELAY.equals(p.path("returnCause").asText(null));
        String trackingCode = p.path("trackingCode").asText(null);
        if (!(entered || left) || trackingCode == null) {
            return null;
        }
        String hub = left && previous.path("custody").hasNonNull("hubId")
                ? previous.path("custody").path("hubId").asText() : p.path("custody").path("hubId").asText(null);
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("trackingCode", trackingCode);
        m.put("tenantId", event.path("tenantId").asText());
        m.put("hubId", hub);
        m.put("packageId", event.path("aggregateId").asText());
        m.put("missionId", p.path("currentDeliveryId").asText(null));
        m.put("deposited", entered);
        m.put("expired", expired);
        return m;
    }

    /**
     * Refreshes the hub occupancy. A failure reaches the container (retries, then {@code tnt.dlq}, decision G.8):
     * replaying the record is harmless, the projection being idempotent.
     */
    private Mono<Void> refreshOccupancy(Map<String, Object> payload, UUID tenantId) {
        UUID coreHubId = uuidVal(payload.get("hubId"));
        if (coreHubId == null) {
            return Mono.empty();
        }
        return hubRepo.findByCoreHubIdAndTenantId(coreHubId, tenantId)
                .flatMap(hub -> occupancyService.getOccupancy(tenantId, hub.getId()).then());
    }

    private Mono<HubParcelRecord> applyProjection(
            HubParcelRecord parcel, Map<String, Object> payload, boolean deposited) {
        Instant now = Instant.now();
        UUID coreEntryId = uuidVal(
                payload.get("hubPackageEntryId"), payload.get("entryId"), payload.get("id"));
        if (coreEntryId != null) {
            parcel.linkCoreEntry(coreEntryId, now);
        }
        if (!deposited && parcel.getStatus() == ParcelStatus.DEPOSITED) {
            parcel.withdraw("Core sync", false, now);
        }
        return parcelRepo.save(HubParcelMapper.toEntity(parcel)).map(HubParcelMapper::toDomain);
    }

    private Mono<HubParcelRecord> createProjection(
            Map<String, Object> payload, UUID tenantId, String trackingCode, boolean deposited) {
        UUID coreHubId = uuidVal(payload.get("hubId"));
        UUID packageId = uuidVal(payload.get("packageId"));
        UUID coreEntryId = uuidVal(
                payload.get("hubPackageEntryId"), payload.get("entryId"), payload.get("id"));
        if (coreHubId == null) {
            return Mono.empty();
        }
        return hubRepo.findByCoreHubIdAndTenantId(coreHubId, tenantId)
                .flatMap(agencyHub -> {
                    Instant now = Instant.now();
                    int retention = retentionHours(agencyHub);
                    HubParcelRecord record = HubParcelRecord.deposit(
                            UUID.randomUUID(), tenantId, agencyHub.getId(),
                            packageId != null ? packageId : UUID.randomUUID(),
                            uuidVal(payload.get("missionId")),
                            trackingCode, retention, now, null, null);
                    if (coreEntryId != null) {
                        record.linkCoreEntry(coreEntryId, now);
                    }
                    if (!deposited) {
                        record.withdraw("Core sync", false, now);
                    }
                    return parcelRepo.save(HubParcelMapper.toEntity(record)).map(HubParcelMapper::toDomain);
                });
    }

    private static int retentionHours(AgencyRelayHubEntity hub) {
        Integer hours = hub.getRetentionDelayHours();
        return hours != null && hours > 0 ? hours : 72;
    }

    private static String stringVal(Object value) {
        return value != null ? value.toString() : null;
    }

    private static UUID uuidVal(Object... candidates) {
        for (Object c : candidates) {
            if (c == null) {
                continue;
            }
            try {
                return UUID.fromString(c.toString());
            } catch (IllegalArgumentException ignored) {
                // try next
            }
        }
        return null;
    }
}
