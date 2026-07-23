package com.yowyob.tiibntick.core.actor.config;

import com.yowyob.tiibntick.core.actor.adapter.in.kafka.FreelancerOrgEventConsumer;
import com.yowyob.tiibntick.core.actor.adapter.in.kafka.IncidentEventConsumer;
import com.yowyob.tiibntick.core.actor.adapter.in.web.DelivererController;
import com.yowyob.tiibntick.core.actor.adapter.in.web.FreelancerController;
import com.yowyob.tiibntick.core.actor.adapter.in.web.ActorKycController;
import com.yowyob.tiibntick.core.actor.adapter.in.web.KycVerificationProxyController;
import com.yowyob.tiibntick.core.actor.adapter.out.auth.ActorCoreYowAuthTntAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.kernel.KernelActorAdapter;
import com.yowyob.tiibntick.core.actor.adapter.out.kernel.KernelKycVerificationAdapter;
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
import com.yowyob.tiibntick.core.actor.application.service.KycVerificationGatewayService;
import com.yowyob.tiibntick.core.actor.application.service.RelayOperatorService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

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
        KycVerificationGatewayService.class,
        // ── Persistence adapters ────────────────────────────────────────────────
        DelivererR2dbcRepositoryAdapter.class,
        FreelancerR2dbcRepositoryAdapter.class,
        RelayOperatorR2dbcRepositoryAdapter.class,
        ClientProfileR2dbcRepositoryAdapter.class,
        // ── Web adapters (controllers) ──────────────────────────────────────────
        DelivererController.class,
        FreelancerController.class,
        ActorKycController.class,
        KycVerificationProxyController.class,
        // ── tnt-auth-core outbound adapter ──────────────────────────────────────
        ActorCoreYowAuthTntAdapter.class,
        // ── Kernel outbound adapter ─────────────────────────────────────────────
        KernelActorAdapter.class,
        KernelKycVerificationAdapter.class,
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

    /**
     * Dedicated consumer factory for tnt-actor-core Kafka listeners, using an explicit
     * {@link StringDeserializer} for both key and value.
     *
     * <p>Fixes Audit n°5 · P-02: without this factory, {@code IncidentEventConsumer} and
     * {@code FreelancerOrgEventConsumer} fell back to Spring Boot's autoconfigured
     * {@code kafkaListenerContainerFactory}, whose value-deserializer is
     * {@code ByteArrayDeserializer} (application.yml) — incompatible with their
     * {@code ConsumerRecord<String, String>} signatures.
     */
    @Bean("actorKafkaConsumerFactory")
    public ConsumerFactory<String, String> actorKafkaConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:tnt-actor-core}") String groupId) {

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Listener container factory backing {@code containerFactory = "actorKafkaListenerContainerFactory"}
     * on tnt-actor-core's {@code @KafkaListener} methods.
     */
    @Bean("actorKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> actorKafkaListenerContainerFactory(
            ConsumerFactory<String, String> actorKafkaConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(actorKafkaConsumerFactory);
        factory.setConcurrency(2);
        return factory;
    }
}