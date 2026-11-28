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

import reactor.core.publisher.Mono;

/**
 * Kafka consumer for delivery-related events that drive loyalty and profile updates.
 *
 * <p>Listens to:
 * <ul>
 *   <li>{@code DeliveryDelivered} of {@code tnt.delivery.events} (lot L8, was {@code tnt.delivery.mission.completed})
 *       — to earn loyalty points and increment delivery count; other types and legacy records are ignored</li>
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
     * Credits loyalty points on {@code DeliveryDelivered} of {@code tnt.delivery.events}; other event types and legacy
     * records are ignored.
     */
    @KafkaListener(
            topics = com.yowyob.tiibntick.common.kafka.TntTopics.DELIVERY_EVENTS,
            groupId = "${tnt.kafka.consumer.groups.tp-core:tnt-tp-core-group}",
            containerFactory = "tntTpKafkaListenerContainerFactory"
    )
    public void onMissionCompleted(ConsumerRecord<String, String> record) throws Exception {
        Map<?, ?> message = objectMapper.readValue(record.value(), Map.class);
        if (!DELIVERED.equals(message.get("eventType")) || message.get("schemaVersion") == null) {
            return;
        }
        CompletedMission mission = parse(message);
        if (mission == null) {
            log.debug("DeliveryDelivered at offset={} carries no sender: no loyalty credit", record.offset());
            return;
        }
        // One message at a time on the listener thread (lot L3): a fire-and-forget subscribe let two deliveries
        // of the same client read and save the same loyalty account concurrently, and one credit was lost.
        // Lot L8: an unreadable record or a failure reaches the container (retries, then tnt.dlq).
        com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport.awaitBounded(
                handle(mission).then(Mono.just(true)), java.time.Duration.ofSeconds(30));
    }

    static final String DELIVERED = "DeliveryDelivered";

    /** What the consumer needs from a completed-delivery message. */
    record CompletedMission(UUID tenantId, UUID senderId, String missionId) { }

    /**
     * Reads a {@code DeliveryDelivered} envelope ({@code {eventType, aggregateId, tenantId, payload:{...delivery
     * snapshot}}}, flat fields accepted too). The sender is {@code payload.senderId}, or the historical
     * {@code senderThirdPartyId}; the mission is {@code missionId}, or the delivery id ({@code aggregateId}).
     *
     * @return {@code null} when no tenant, sender or mission can be read
     */
    static CompletedMission parse(Map<?, ?> message) {
        Map<?, ?> nested = message.get("payload") instanceof Map<?, ?> m ? m : Map.of();
        UUID tenantId = uuid(first(message, nested, "tenantId"));
        UUID senderId = uuid(first(message, nested, "senderId"));
        if (senderId == null) senderId = uuid(first(message, nested, "senderThirdPartyId"));
        Object missionRaw = first(message, nested, "missionId");
        if (missionRaw == null) missionRaw = first(message, nested, "aggregateId");
        String missionId = missionRaw != null ? missionRaw.toString() : null;
        if (tenantId == null || senderId == null || missionId == null) {
            return null;
        }
        return new CompletedMission(tenantId, senderId, missionId);
    }

    Mono<Void> handle(CompletedMission mission) {
        EarnLoyaltyPointsCommand earnCmd = new EarnLoyaltyPointsCommand(
                mission.tenantId(), mission.senderId(), POINTS_PER_DELIVERY, mission.missionId());
        return loyaltyService.creditCompletedDelivery(earnCmd)
                .doOnNext(account -> log.info(
                        "Loyalty points credited: thirdParty={}, points={}, balance={}",
                        mission.senderId(), POINTS_PER_DELIVERY, account.getAvailablePoints()))
                .map(account -> true)
                // empty: this mission was already credited (redelivered message), nothing more to count
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.info("Mission {} already credited to thirdParty={}: ignored", mission.missionId(),
                            mission.senderId());
                    return false;
                }))
                // a failed credit reaches the container (retries, then tnt.dlq — G.8): the credit is idempotent per
                // mission, so a retry never credits twice
                // the tp-core client profile is optional (a Go-Freelancer client has none)
                .flatMap(firstTime -> firstTime
                        ? profileService.incrementDeliveriesIfProfiled(mission.tenantId(), mission.senderId()).then()
                        : Mono.<Void>empty())
                .then();
    }

    private static Object first(Map<?, ?> top, Map<?, ?> nested, String key) {
        Object v = top.get(key);
        return v != null ? v : nested.get(key);
    }

    private static UUID uuid(Object raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
