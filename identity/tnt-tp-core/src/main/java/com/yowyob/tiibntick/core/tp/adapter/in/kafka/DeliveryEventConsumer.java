package com.yowyob.tiibntick.core.tp.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.tp.application.port.in.command.EarnLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.service.LoyaltyService;
import com.yowyob.tiibntick.core.tp.application.service.TntClientProfileService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for delivery-related events that drive loyalty and profile updates.
 *
 * <p>Listens to:
 * <ul>
 *   <li>{@code tnt.delivery.mission.completed} — to earn loyalty points and increment delivery count</li>
 * </ul>
 * </p>
 *
 * @author MANFOUO Braun
 */
@Component
public class DeliveryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventConsumer.class);
    private static final int POINTS_PER_DELIVERY = 10;

    private final LoyaltyService loyaltyService;
    private final TntClientProfileService profileService;
    private final ObjectMapper objectMapper;

    public DeliveryEventConsumer(
            LoyaltyService loyaltyService,
            TntClientProfileService profileService,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.loyaltyService = loyaltyService;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles mission completed events to credit loyalty points and increment delivery counters.
     */
    @KafkaListener(
            topics = "${tnt.kafka.topics.delivery-mission-completed:tnt.delivery.mission.completed}",
            groupId = "${tnt.kafka.consumer.groups.tp-core:tnt-tp-core-group}",
            containerFactory = "tntTpKafkaListenerContainerFactory"
    )
    public void onMissionCompleted(ConsumerRecord<String, String> record) {
        log.info("Received MissionCompleted event, offset={}, partition={}",
                record.offset(), record.partition());
        try {
            Map<?, ?> payload = objectMapper.readValue(record.value(), Map.class);

            UUID tenantId = UUID.fromString((String) payload.get("tenantId"));
            UUID senderThirdPartyId = payload.get("senderThirdPartyId") != null
                    ? UUID.fromString((String) payload.get("senderThirdPartyId")) : null;
            String missionId = (String) payload.get("missionId");

            if (senderThirdPartyId != null && missionId != null) {
                EarnLoyaltyPointsCommand earnCmd = new EarnLoyaltyPointsCommand(
                        tenantId, senderThirdPartyId, POINTS_PER_DELIVERY, missionId);

                loyaltyService.earn(earnCmd)
                        .doOnSuccess(account -> log.info(
                                "Loyalty points credited: thirdParty={}, points={}, balance={}",
                                senderThirdPartyId, POINTS_PER_DELIVERY, account.getAvailablePoints()))
                        .doOnError(e -> log.error(
                                "Failed to credit loyalty points for thirdParty={}: {}",
                                senderThirdPartyId, e.getMessage()))
                        .then(profileService.incrementDeliveries(tenantId, senderThirdPartyId))
                        .subscribe();
            }
        } catch (Exception e) {
            log.error("Failed to process MissionCompleted event from offset={}: {}",
                    record.offset(), e.getMessage(), e);
        }
    }
}
