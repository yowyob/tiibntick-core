package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRelayHubEntity;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubParcelRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.application.mapper.HubParcelMapper;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubOccupancyService;
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
 * Inventory Kafka → Agency hubops: parcel projection + occupancy refresh.
 */
@Component
@ConditionalOnProperty(name = "tnt.agency.kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryHubConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryHubConsumer.class);

    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final HubParcelRecordR2dbcRepository parcelRepo;
    private final HubOccupancyService occupancyService;
    private final ObjectMapper objectMapper;

    public InventoryHubConsumer(AgencyRelayHubR2dbcRepository hubRepo,
                                HubParcelRecordR2dbcRepository parcelRepo,
                                HubOccupancyService occupancyService,
                                ObjectMapper objectMapper) {
        this.hubRepo = hubRepo;
        this.parcelRepo = parcelRepo;
        this.occupancyService = occupancyService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-inventory-deposited}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-inventory:tnt-agency-erp-inventory-consumer}",
            containerFactory = "agencyOrgHubKafkaListenerFactory"
    )
    public void onPackageDeposited(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, true);
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-inventory-pickedup}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-inventory:tnt-agency-erp-inventory-consumer}",
            containerFactory = "agencyOrgHubKafkaListenerFactory"
    )
    public void onPackagePickedUp(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, false);
    }

    private void handle(ConsumerRecord<String, String> record, Acknowledgment ack, boolean deposited) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
            String trackingCode = stringVal(payload.get("trackingCode"));
            String tenantIdStr = stringVal(payload.get("tenantId"));
            if (trackingCode == null || tenantIdStr == null) {
                ack.acknowledge();
                return;
            }
            UUID tenantId = UUID.fromString(tenantIdStr);
            parcelRepo.findByTrackingCodeAndTenantId(trackingCode, tenantId)
                    .map(HubParcelMapper::toDomain)
                    .flatMap(parcel -> applyProjection(parcel, payload, deposited))
                    .switchIfEmpty(Mono.defer(() -> createProjection(payload, tenantId, trackingCode, deposited)))
                    .flatMap(parcel -> refreshOccupancy(payload, tenantId).thenReturn(parcel))
                    .doOnError(e -> log.warn("[InventoryHubConsumer] sync failed code={}: {}",
                            trackingCode, e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .doFinally(s -> ack.acknowledge())
                    .subscribe();
        } catch (Exception e) {
            log.warn("[InventoryHubConsumer] parse error: {}", e.getMessage());
            ack.acknowledge();
        }
    }

    private Mono<Void> refreshOccupancy(Map<String, Object> payload, UUID tenantId) {
        UUID coreHubId = uuidVal(payload.get("hubId"));
        if (coreHubId == null) {
            return Mono.empty();
        }
        return hubRepo.findByCoreHubIdAndTenantId(coreHubId, tenantId)
                .flatMap(hub -> occupancyService.getOccupancy(tenantId, hub.getId()).then())
                .onErrorResume(e -> Mono.empty());
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
                            trackingCode, retention, now);
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
