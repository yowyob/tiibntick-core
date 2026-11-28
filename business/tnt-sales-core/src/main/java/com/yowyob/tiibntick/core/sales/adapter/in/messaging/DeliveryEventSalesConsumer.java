package com.yowyob.tiibntick.core.sales.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.sales.application.service.SalesApplicationService;
import com.yowyob.tiibntick.core.sales.domain.model.ReturnReason;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Moves the sales orders dispatched with a delivery along with it (lot L8, spec §3.7), from {@code tnt.delivery.events}:
 * {@code DeliveryPickedUp} → IN_DELIVERY, {@code DeliveryDelivered} → DELIVERED, {@code DeliveryFailed} → RETURNED.
 * The order is found by its delivery ({@code mission_id}); other types and legacy records are ignored. The work is
 * awaited (lot L7 rule): a failure is retried, then dead-lettered to {@code tnt.dlq}.
 *
 * <p>Replaces the listeners of {@code tnt.delivery.mission.started / completed / failed}, which read an
 * {@code orderId} no delivery event ever carried.
 *
 * Author: MANFOUO Braun
 */
@Component
public class DeliveryEventSalesConsumer {

    static final Set<String> FOLLOWED = Set.of("DeliveryPickedUp", "DeliveryDelivered", "DeliveryFailed");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final SalesApplicationService salesService;
    private final ObjectMapper objectMapper;

    public DeliveryEventSalesConsumer(SalesApplicationService salesService,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.salesService = salesService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TntTopics.DELIVERY_EVENTS, groupId = "tnt-sales-core-delivery",
            containerFactory = "salesKafkaListenerContainerFactory")
    public void onDeliveryEvent(ConsumerRecord<String, String> record) throws Exception {
        JsonNode event = objectMapper.readTree(record.value());
        String type = event.path("eventType").asText();
        if (!TntAggregateEvent.isAggregateEvent(event) || !FOLLOWED.contains(type)
                || !event.hasNonNull("tenantId")) {
            return;
        }
        String reason = event.path("payload").path("failureReason").asText(null);
        ReactiveListenerSupport.awaitBounded(salesService.followDelivery(
                        UUID.fromString(event.path("tenantId").asText()),
                        UUID.fromString(event.path("aggregateId").asText()), type, toReturnReason(reason),
                        "Auto-returned from delivery failure: " + (reason != null ? reason : "UNKNOWN"))
                .then(Mono.just(true)), TIMEOUT);
    }

    /** Delivery failure reason (spec §1.2) → return reason of the order. */
    static ReturnReason toReturnReason(String failureReason) {
        if (failureReason == null) {
            return ReturnReason.OTHER;
        }
        return switch (failureReason) {
            case "CLIENT_REFUSED_RECEPTION", "CLIENT_REFUSED_OTP_VALIDATION" -> ReturnReason.CUSTOMER_REFUSAL;
            case "CLIENT_ABSENT_AT_DELIVERY", "CLIENT_PHONE_UNREACHABLE", "NOT_COLLECTED_AT_RELAY",
                 "MAX_ATTEMPTS_REACHED" -> ReturnReason.RECIPIENT_ABSENT;
            case "CLIENT_WRONG_ADDRESS", "CLIENT_INCOMPLETE_ADDRESS", "CLIENT_BUILDING_ACCESS_BLOCKED" ->
                    ReturnReason.WRONG_ADDRESS;
            case "PARCEL_DAMAGED" -> ReturnReason.DAMAGED_GOODS;
            default -> ReturnReason.OTHER;
        };
    }
}
