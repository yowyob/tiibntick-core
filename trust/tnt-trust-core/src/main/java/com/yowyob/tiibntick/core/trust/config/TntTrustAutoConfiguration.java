package com.yowyob.tiibntick.core.trust.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Spring Boot Autoconfiguration — {@code TntTrustAutoConfiguration}.
 *
 * <p>Entry point for the {@code tnt-trust} JAR library. Activated automatically
 * when the JAR is on the classpath of {@code tnt-bootstrap} or any consuming module.
 *
 * <h3>Beans registered</h3>
 * <ul>
 *   <li>All services: {@link com.yowyob.tiibntick.core.trust.application.service.DeliveryProofChainService},
 *       {@link com.yowyob.tiibntick.core.trust.application.service.DIDManagerService},
 *       {@link com.yowyob.tiibntick.core.trust.application.service.PolChainService},
 *       {@link com.yowyob.tiibntick.core.trust.application.service.BadgeChainService},
 *       {@link com.yowyob.tiibntick.core.trust.application.service.DaoRuleChainService},
 *       {@link com.yowyob.tiibntick.core.trust.application.service.LogisticEventPublisherService}</li>
 *   <li>All adapters (Kafka, REST, R2DBC, Incident)</li>
 *   <li>WebClient targeting the Trust Event internal REST API</li>
 *   <li>Kafka Listener Container Factory for {@code tnt-trust}</li>
 *   <li>{@link com.yowyob.tiibntick.core.trust.adapter.out.incident.IncidentBlockchainAuditAdapter}
 *       — implements {@code tnt-incident-core}'s {@code IBlockchainAuditPort} directly</li>
 * </ul>
 *
 * <h3>Kafka topics created at startup</h3>
 * <ul>
 *   <li>{@code tnt.trust.logistic.events} — internal topic for tnt-trust events (optional)</li>
 * </ul>
 *
 * <h3>tnt-auth-core integration</h3>
 * <p>{@code tnt-auth-core} is imported as a dependency. Its auto-configuration
 * registers {@code TntCurrentUserArgumentResolver} in WebFlux, enabling
 * {@code @CurrentUser TntUserIdentity} injection in {@code TrustApiController}.
 *
 * <h3>tnt-roles-core integration</h3>
 * <p>{@code tnt-roles-core} is imported as a dependency. Its auto-configuration
 * registers {@code TntPermissionAspect}, enabling declarative
 * {@code @RequirePermission} enforcement on all service methods.
 *
 * <h3>Deployment toggle (§15.1 of {@code TNT_CORE_Connexion_Trust_Module.md})</h3>
 * <p>{@code tnt.trust.enabled=false} (default {@code true}) switches off this
 * entire autoconfiguration — no {@code tnt-trust-core} bean is registered at
 * all, for environments without a Kernel Trust stack. <strong>Caveat:</strong>
 * this repo's calling modules ({@code tnt-delivery-core}, {@code tnt-incident-core},
 * {@code tnt-billing-pricing}, {@code tnt-billing-wallet}, {@code tnt-dispute-core},
 * {@code tnt-realtime-core}, {@code tnt-organization-core}) currently inject their
 * outbound trust ports as required (non-optional) beans; disabling this module
 * without also making those injections optional (or providing the no-op
 * fallback adapters the design doc describes) will fail their startup. Treat
 * {@code tnt.trust.enabled=false} as usable today only when none of those
 * modules are also on the classpath — full safe toggling across all seven
 * calling modules is a separate, larger cross-module change.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "tnt.trust", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TntTrustProperties.class)
@EnableScheduling
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.trust")
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.trust.adapter.out.persistence")
public class TntTrustAutoConfiguration {

    /**
     * Reactive WebClient for calling the Trust Event internal REST API.
     * Configured with a timeout appropriate for Cameroon network conditions.
     */
    @Bean("trustEventWebClient")
    @ConditionalOnMissingBean(name = "trustEventWebClient")
    public WebClient trustEventWebClient(final TntTrustProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getTrustEventBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("X-Internal-Token", "${tnt.trust.internal-token:kernel-internal}")
                // Connect + read timeout
                .clientConnector(new org.springframework.http.client.reactive
                        .ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(Duration.ofMillis(properties.getRestClientTimeoutMs()))))
                .build();
    }

    /**
     * Kafka Listener Container Factory for tnt-trust consumed topics
     * ({@code yow.trust.events.committed}).
     */
    @Bean("tntTrustKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "tntTrustKafkaListenerContainerFactory")
    public org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, String>
    tntTrustKafkaListenerContainerFactory(
            @org.springframework.beans.factory.annotation.Qualifier("tntTrustConsumerFactory")
            final org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory) {
        final org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(1);
        return factory;
    }

    /**
     * Fallback {@link ObjectMapper} with JSR-310 (Java time) support.
     * The host application's ObjectMapper will take precedence.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper tntTrustObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Fallback {@link MeterRegistry} for standalone use.
     * In production, Prometheus registry from tnt-bootstrap takes precedence.
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry tntTrustMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
