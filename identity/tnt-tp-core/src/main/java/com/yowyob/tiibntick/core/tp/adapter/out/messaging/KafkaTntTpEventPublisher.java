package com.yowyob.tiibntick.core.tp.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Kafka adapter for publishing tnt-tp-core domain events.
 *
 * @author MANFOUO Braun
 */
@Component
public class KafkaTntTpEventPublisher implements TntTpEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaTntTpEventPublisher.class);

    private static final String TOPIC_CLIENT_PROFILE  = "tnt.tp.client.profile.events";
    private static final String TOPIC_KYC             = "tnt.tp.kyc.events";
    private static final String TOPIC_LOYALTY         = "tnt.tp.loyalty.events";
    private static final String TOPIC_RATING          = "tnt.tp.rating.events";
    private static final String TOPIC_PHONE_ALIAS     = "tnt.tp.phone.alias.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaTntTpEventPublisher(
            @Qualifier("tntTpKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("tntTpObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(Object event) {
        return Mono.fromCallable(() -> {
                    String topic = resolveTopic(event);
                    String key   = resolveKey(event);
                    String value = serialize(event);
                    kafkaTemplate.send(topic, key, value);
                    log.debug("Published event type={} to topic={}", event.getClass().getSimpleName(), topic);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> publishAll(List<Object> events) {
        return Mono.fromRunnable(() ->
                events.forEach(event -> publish(event).subscribe())
        );
    }

    private String resolveTopic(Object event) {
        return switch (event) {
            case ClientProfileRegisteredEvent ignored   -> TOPIC_CLIENT_PROFILE;
            case ClientProfileDeactivatedEvent ignored  -> TOPIC_CLIENT_PROFILE;
            case KycStatusChangedEvent ignored          -> TOPIC_KYC;
            case LoyaltyPointsEarnedEvent ignored       -> TOPIC_LOYALTY;
            case LoyaltyPointsRedeemedEvent ignored     -> TOPIC_LOYALTY;
            case ThirdPartyRatedEvent ignored           -> TOPIC_RATING;
            case PhoneAliasAssignedEvent ignored        -> TOPIC_PHONE_ALIAS;
            default -> "tnt.tp.unknown.events";
        };
    }

    private String resolveKey(Object event) {
        return switch (event) {
            case ClientProfileRegisteredEvent e  -> e.thirdPartyId().toString();
            case ClientProfileDeactivatedEvent e -> e.thirdPartyId().toString();
            case KycStatusChangedEvent e         -> e.thirdPartyId().toString();
            case LoyaltyPointsEarnedEvent e      -> e.thirdPartyId().toString();
            case LoyaltyPointsRedeemedEvent e    -> e.thirdPartyId().toString();
            case ThirdPartyRatedEvent e          -> e.ratedThirdPartyId().toString();
            case PhoneAliasAssignedEvent e       -> e.thirdPartyId().toString();
            default -> "unknown";
        };
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event: " + event.getClass().getSimpleName(), e);
        }
    }
}
