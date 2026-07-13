package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubOccupancyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "tnt.agency.kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryHubConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryHubConsumer.class);

    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final HubOccupancyService occupancyService;
    private final ObjectMapper objectMapper;

    public InventoryHubConsumer(AgencyRelayHubR2dbcRepository hubRepo,
                                HubOccupancyService occupancyService,
                                ObjectMapper objectMapper) {
        this.hubRepo = hubRepo;
        this.occupancyService = occupancyService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-inventory-deposited}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-inventory:tnt-agency-erp-inventory-consumer}",
            containerFactory = "agencyOrgHubKafkaListenerFactory"
    )
    public void onPackageDeposited(ConsumerRecord<String, String> record, Acknowledgment ack) {
        syncOccupancy(record, ack);
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-inventory-pickedup}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-inventory:tnt-agency-erp-inventory-consumer}",
            containerFactory = "agencyOrgHubKafkaListenerFactory"
    )
    public void onPackagePickedUp(ConsumerRecord<String, String> record, Acknowledgment ack) {
        syncOccupancy(record, ack);
    }

    private void syncOccupancy(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
            String tenantIdStr = stringVal(payload.get("tenantId"));
            UUID coreHubId = uuidVal(payload.get("hubId"));
            if (tenantIdStr == null || coreHubId == null) {
                ack.acknowledge();
                return;
            }
            UUID tenantId = UUID.fromString(tenantIdStr);
            hubRepo.findByCoreHubIdAndTenantId(coreHubId, tenantId)
                    .flatMap(hub -> occupancyService.getOccupancy(tenantId, hub.getId()).then())
                    .doOnError(e -> log.warn("[InventoryHubConsumer] sync failed hub={}: {}",
                            coreHubId, e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .doFinally(s -> ack.acknowledge())
                    .subscribe();
        } catch (Exception e) {
            log.warn("[InventoryHubConsumer] parse error: {}", e.getMessage());
            ack.acknowledge();
        }
    }

    private static String stringVal(Object value) {
        return value != null ? value.toString() : null;
    }

    private static UUID uuidVal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
