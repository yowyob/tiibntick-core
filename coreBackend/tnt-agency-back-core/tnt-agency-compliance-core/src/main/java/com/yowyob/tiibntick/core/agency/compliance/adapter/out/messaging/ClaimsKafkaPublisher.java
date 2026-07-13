package com.yowyob.tiibntick.core.agency.compliance.adapter.out.messaging;

import com.yowyob.tiibntick.core.agency.compliance.application.port.out.ClaimEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes client claim notifications to {@code tnt.agency.claims.submitted}. Reuses the
 * agency eventing Kafka template. Ported from the BFF {@code ComplianceKafkaPublisher}.
 */
@Component
public class ClaimsKafkaPublisher implements ClaimEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(ClaimsKafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String claimsTopic;

    public ClaimsKafkaPublisher(
            @Qualifier("agencyEventKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${tnt.kafka.topics.produced.claims:tnt.agency.claims.submitted}") String claimsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.claimsTopic = claimsTopic;
    }

    @Override
    public Mono<String> submitClaim(ClaimSubmission submission) {
        String claimRef = "CLM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return Mono.fromCallable(() -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("claimRef", claimRef);
                    payload.put("tenantId", submission.tenantId().toString());
                    payload.put("agencyId", submission.agencyId().toString());
                    payload.put("missionId", submission.missionId() != null
                            ? submission.missionId().toString() : null);
                    payload.put("claimType", submission.claimType());
                    payload.put("description", submission.description());
                    payload.put("contactEmail", submission.contactEmail());
                    return kafkaTemplate.send(claimsTopic, submission.tenantId().toString(), payload);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.warn("[Claims] publish failed agency={}: {}",
                        submission.agencyId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .thenReturn(claimRef);
    }
}
