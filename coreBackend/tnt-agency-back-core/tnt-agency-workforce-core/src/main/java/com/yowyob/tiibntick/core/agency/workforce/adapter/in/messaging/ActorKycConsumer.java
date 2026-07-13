package com.yowyob.tiibntick.core.agency.workforce.adapter.in.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.workforce.application.service.AgencyDelivererService;
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
public class ActorKycConsumer {

    private static final Logger log = LoggerFactory.getLogger(ActorKycConsumer.class);

    private final AgencyDelivererService delivererService;
    private final ObjectMapper objectMapper;

    public ActorKycConsumer(AgencyDelivererService delivererService, ObjectMapper objectMapper) {
        this.delivererService = delivererService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${tnt.kafka.topics.consumed.core-actor-kyc}",
            groupId = "${tnt.kafka.topics.groups.agency-erp-actor:tnt-agency-erp-actor-consumer}",
            containerFactory = "agencyWorkforceKafkaListenerFactory"
    )
    public void onKycValidated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
            UUID actorId = UUID.fromString((String) payload.get("actorId"));
            UUID tenantId = UUID.fromString((String) payload.get("tenantId"));
            delivererService.reactivateByActorId(tenantId, actorId)
                    .doOnSuccess(d -> log.info("[ActorKycConsumer] deliverer reactivated actorId={}", actorId))
                    .doOnError(e -> log.warn("[ActorKycConsumer] failed actorId={}: {}", actorId, e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .doFinally(s -> ack.acknowledge())
                    .subscribe();
        } catch (Exception e) {
            log.warn("[ActorKycConsumer] parse error: {}", e.getMessage());
            ack.acknowledge();
        }
    }
}
