package com.yowyob.tiibntick.core.actor.config;

import com.yowyob.tiibntick.core.actor.adapter.in.kafka.FreelancerOrgEventConsumer;
import com.yowyob.tiibntick.core.actor.adapter.in.kafka.IncidentEventConsumer;
import com.yowyob.tiibntick.core.actor.adapter.in.web.DelivererController;
import com.yowyob.tiibntick.core.actor.adapter.in.web.FreelancerController;
import com.yowyob.tiibntick.core.actor.adapter.in.web.ActorKycController;
import com.yowyob.tiibntick.core.actor.adapter.out.auth.ActorCoreYowAuthTntAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.incident.ActorReputationPortAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.kernel.KernelActorAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.messaging.KafkaActorEventPublisher;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.ClientProfileR2dbcRepositoryAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.DelivererR2dbcRepositoryAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.FreelancerR2dbcRepositoryAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.persistence.RelayOperatorR2dbcRepositoryAdapter;
import com.yowyob.tiibntick.core.actor.application.service.ActorBadgeService;
import com.yowyob.tiibntick.core.actor.application.service.ActorPerformanceService;
import com.yowyob.tiibntick.core.actor.application.service.ActorRatingService;
import com.yowyob.tiibntick.core.actor.application.service.ClientProfileService;
import com.yowyob.tiibntick.core.actor.application.service.DelivererAvailabilityService;
import com.yowyob.tiibntick.core.actor.application.service.DelivererLocationService;
import com.yowyob.tiibntick.core.actor.application.service.DelivererService;
import com.yowyob.tiibntick.core.actor.application.service.FreelancerAssociationService;
import com.yowyob.tiibntick.core.actor.application.service.FreelancerOrgLinkService;
import com.yowyob.tiibntick.core.actor.application.service.FreelancerService;
import com.yowyob.tiibntick.core.actor.application.service.ActorKycService;
import com.yowyob.tiibntick.core.actor.application.service.RelayOperatorService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for the {@code tnt-actor-core} module.
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackageClasses = {
        // ── Application services ────────────────────────────────────────────────
        DelivererService.class,
        FreelancerService.class,
        RelayOperatorService.class,
        ClientProfileService.class,
        DelivererLocationService.class,
        DelivererAvailabilityService.class,
        FreelancerAssociationService.class,
        FreelancerOrgLinkService.class,
        ActorRatingService.class,
        ActorBadgeService.class,
        ActorKycService.class,
        ActorPerformanceService.class,
        // ── Persistence adapters ────────────────────────────────────────────────
        DelivererR2dbcRepositoryAdapter.class,
        FreelancerR2dbcRepositoryAdapter.class,
        RelayOperatorR2dbcRepositoryAdapter.class,
        ClientProfileR2dbcRepositoryAdapter.class,
        // ── Web adapters (controllers) ──────────────────────────────────────────
        DelivererController.class,
        FreelancerController.class,
        ActorKycController.class,
        // ── tnt-auth-core outbound adapter ──────────────────────────────────────
        ActorCoreYowAuthTntAdapter.class,
        // ── tnt-incident-core outbound adapter ─────────────────────────────────
        ActorReputationPortAdapter.class,
        // ── Kernel outbound adapter ─────────────────────────────────────────────
        KernelActorAdapter.class,
        // ── Kafka publisher ─────────────────────────────────────────────────────
        KafkaActorEventPublisher.class,
        // ── Kafka consumers ─────────────────────────────────────────────────────
        IncidentEventConsumer.class,
        FreelancerOrgEventConsumer.class
})

@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.actor.adapter.out.persistence",
        entityOperationsRef = "r2dbcEntityTemplate" 
)
public class ActorCoreConfig {

    @Bean
    public KafkaTemplate<String, String> actorKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }
}