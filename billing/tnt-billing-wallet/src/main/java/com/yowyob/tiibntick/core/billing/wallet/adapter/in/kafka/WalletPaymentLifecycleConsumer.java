package com.yowyob.tiibntick.core.billing.wallet.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntAggregateEvent;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.billing.wallet.application.service.PaymentLifecycleService;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code tnt.delivery.events} and {@code tnt.dispute.events} → escrow and cash collection (spec §4.2, lot L5).
 * Blocking (lot L7 rule): a Kernel failure reaches the container (retries, then {@code tnt.dlq}); records that are not
 * aggregate events, and event types without money effect, are ignored.
 */
@Slf4j
@Component
public class WalletPaymentLifecycleConsumer {

    private final PaymentLifecycleService lifecycle;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    public WalletPaymentLifecycleConsumer(PaymentLifecycleService lifecycle,
                                          @Qualifier("walletObjectMapper") ObjectMapper objectMapper,
                                          ObjectProvider<ReactiveListenerSupport> listenerSupport) {
        this.lifecycle = lifecycle;
        this.objectMapper = objectMapper;
        this.listenerSupport = listenerSupport.getIfAvailable(ReactiveListenerSupport::withDefaultTimeout);
    }

    @KafkaListener(topics = TntTopics.DELIVERY_EVENTS, groupId = "tnt-billing-wallet-delivery",
            containerFactory = "walletKafkaListenerContainerFactory")
    public void onDeliveryEvent(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        if (!TntAggregateEvent.isAggregateEvent(root)) {
            return;
        }
        listenerSupport.await(deliveryEffect(root));
    }

    @KafkaListener(topics = TntTopics.DISPUTE_EVENTS, groupId = "tnt-billing-wallet-dispute",
            containerFactory = "walletKafkaListenerContainerFactory")
    public void onDisputeEvent(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        if (!TntAggregateEvent.isAggregateEvent(root)) {
            return;
        }
        listenerSupport.await(disputeEffect(root));
    }

    Mono<Void> deliveryEffect(JsonNode root) {
        UUID deliveryId = UUID.fromString(root.path("aggregateId").asText());
        JsonNode details = root.path("details");
        return courierChange(root, deliveryId, details).then(Mono.defer(() -> moneyEffect(root, deliveryId, details)));
    }

    /** QO-11 / D-153: a delivery event whose details name a replacement courier records the courier legs. */
    private Mono<Void> courierChange(JsonNode root, UUID deliveryId, JsonNode details) {
        UUID replaced = uuid(text(details.path("replacedCourierId")));
        UUID replacement = uuid(text(details.path("replacementCourierId")));
        if (replaced == null || replacement == null) {
            return Mono.empty();
        }
        return lifecycle.courierReplaced(new CourierLeg(null, uuid(text(root.path("tenantId"))), deliveryId,
                uuid(text(root.path("eventId"))), root.path("aggregateVersion").asLong(0L), replaced, replacement,
                amount(details.path("replacedLegKm")), amount(details.path("remainingKm")), null));
    }

    private Mono<Void> moneyEffect(JsonNode root, UUID deliveryId, JsonNode details) {
        return switch (root.path("eventType").asText()) {
            case "DeliveryDelivered" -> lifecycle.delivered(deliveryId, amount(root.path("payload").path("agreedAmount")));
            case "DeliveryAtRelayHub" -> lifecycle.depositedAtRelay(deliveryId, details.path("atDestination").asBoolean(false));
            case "DeliveryCancelled" -> lifecycle.cancelled(deliveryId, text(root.path("previousState").path("status")),
                    text(details.path("cancelledBy")));
            case "DeliveryFailed" -> lifecycle.failed(deliveryId, text(details.path("reason")),
                    text(details.path("responsibleParty")));
            default -> Mono.empty();
        };
    }

    Mono<Void> disputeEffect(JsonNode root) {
        JsonNode payload = root.path("payload");
        String delivery = text(payload.path("deliveryId"));
        if (delivery == null) {
            return Mono.empty();
        }
        UUID deliveryId = UUID.fromString(delivery);
        UUID disputeId = UUID.fromString(root.path("aggregateId").asText());
        String type = root.path("eventType").asText();
        Mono<Void> debt = "DisputeCompensated".equals(type)
                ? lifecycle.disputeCompensated(uuid(text(root.path("tenantId"))), deliveryId, disputeId,
                        text(payload.path("responsibleParty")), amount(payload.path("compensationAmount")),
                        text(payload.path("currency")))
                : Mono.empty();
        return switch (type) {
            case "DisputeOpen" -> lifecycle.disputeOpened(deliveryId, disputeId);
            case "DisputeCompensated", "DisputeClosedResolved", "DisputeClosedWithdrawn", "DisputeClosedExpired" ->
                    lifecycle.disputeClosed(deliveryId, disputeId,
                            "DisputeClosedWithdrawn".equals(root.path("eventType").asText())
                                    || "DisputeClosedExpired".equals(root.path("eventType").asText())
                                    ? null : text(payload.path("resolutionType")),
                            text(payload.path("responsibleParty"))).then(debt);
            default -> Mono.empty();
        };
    }

    private static UUID uuid(String s) {
        return s == null ? null : UUID.fromString(s);
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() || n.asText().isBlank() ? null : n.asText();
    }

    private static BigDecimal amount(JsonNode n) {
        String s = text(n);
        return s == null ? null : new BigDecimal(s);
    }
}
