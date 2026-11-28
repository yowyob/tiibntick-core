package com.yowyob.tiibntick.core.agency.workforce.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyDelivererService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Reactivates the agency deliverer of an actor whose KYC was validated.
 *
 * <p>Lot L7 (spec §3.10): the work is awaited on the container thread ({@link ReactiveListenerSupport}) and the
 * record acknowledged only once it succeeded; any failure (unreadable record, missing ids, database error) reaches
 * the container: retries, then {@code tnt.dlq}. An actor that is not an agency deliverer is a no-op.
 */
@Component
@ConditionalOnProperty(name = "tnt.agency.kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class ActorKycConsumer {

    private static final Logger log = LoggerFactory.getLogger(ActorKycConsumer.class);

    private final AgencyDelivererService delivererService;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    public ActorKycConsumer(AgencyDelivererService delivererService, ObjectMapper objectMapper,
                            ReactiveListenerSupport listenerSupport) {
        this.delivererService = delivererService;
        this.objectMapper = objectMapper;
        this.listenerSupport = listenerSupport;
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-actor-kyc}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-actor:tnt-agency-erp-actor-consumer}",
            containerFactory = "agencyWorkforceKafkaListenerFactory"
    )
    public void onKycValidated(ConsumerRecord<String, String> record, Acknowledgment ack) throws IOException {
        Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
        UUID actorId = UUID.fromString(String.valueOf(payload.get("actorId")));
        UUID tenantId = UUID.fromString(String.valueOf(payload.get("tenantId")));
        listenerSupport.await(delivererService.reactivateByActorId(tenantId, actorId));
        log.info("[ActorKycConsumer] KYC validation handled actorId={}", actorId);
        ack.acknowledge();
    }
}
