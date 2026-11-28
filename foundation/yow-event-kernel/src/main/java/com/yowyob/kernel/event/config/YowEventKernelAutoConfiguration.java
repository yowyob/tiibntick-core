package com.yowyob.kernel.event.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.yowyob.kernel.event.application.port.in.ManageSchemaUseCase;
import com.yowyob.kernel.event.application.port.in.ProcessDeadLetterUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventBatchUseCase;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.application.port.in.QueryEventStatsUseCase;
import com.yowyob.kernel.event.application.port.in.QueryEventUseCase;
import com.yowyob.kernel.event.application.port.in.ReplayEventUseCase;
import com.yowyob.kernel.event.application.port.out.DeadLetterRepository;
import com.yowyob.kernel.event.application.port.out.EventEnvelopeRepository;
import com.yowyob.kernel.event.application.port.out.EventIdempotencyStorePort;
import com.yowyob.kernel.event.application.port.out.EventSchemaRepository;
import com.yowyob.kernel.event.application.port.out.KafkaPublisherPort;
import com.yowyob.kernel.event.application.port.out.OutboxEntryRepository;
import com.yowyob.kernel.event.application.service.DeadLetterService;
import com.yowyob.kernel.event.application.service.EventPublisherService;
import com.yowyob.kernel.event.application.service.EventQueryService;
import com.yowyob.kernel.event.application.service.EventStatsService;
import com.yowyob.kernel.event.application.service.ReplayEventService;
import com.yowyob.kernel.event.application.service.SchemaRegistryService;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
import com.yowyob.kernel.event.application.port.out.BrokerAvailabilityPort;
import com.yowyob.kernel.event.application.port.out.ProcessedEventStorePort;
import com.yowyob.kernel.event.application.service.OutboxRelayService;
import com.yowyob.kernel.event.application.service.ProcessedEventPurgeService;
import com.yowyob.kernel.event.adapter.kafka.consumer.IdempotentListenerContainerFactoryPostProcessor;
import com.yowyob.kernel.event.adapter.kafka.consumer.IdempotentRecordInterceptor;
import com.yowyob.kernel.event.adapter.kafka.consumer.TntKafkaErrorHandlingPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.env.Environment;
import com.yowyob.kernel.event.domain.vo.RetryPolicy;

import java.util.Map;

/**
 * Spring Boot autoconfiguration for the {@code yow-event-kernel} module.
 *
 * <p>This class is discovered automatically by Spring Boot via the
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * file included in the module JAR.
 *
 * <p><strong>Design decisions:</strong>
 * <ul>
 *   <li>All beans are registered with {@code @ConditionalOnMissingBean} so that
 *       consuming applications (e.g. {@code tnt-bootstrap}) can override any
 *       component without touching this class.</li>
 *   <li>{@code @ComponentScan} is scoped to {@code com.yowyob.kernel.event} to avoid
 *       accidentally picking up beans from the consuming application's classpath.</li>
 *   <li>{@code @EnableScheduling} activates the outbox relay and the processed-event purge.</li>
 * </ul>
 * @author MANFOUO Braun
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(YowEventKernelProperties.class)
@ComponentScan(basePackages = "com.yowyob.kernel.event")
public class YowEventKernelAutoConfiguration {

    // ── Kafka AdminClient ────────────────────────────────────────────────────

    /**
     * Provides a Kafka {@link AdminClient} used by {@code KafkaTopicManagerAdapter}
     * to create and verify topics at startup.
     */
    @Bean
    @ConditionalOnMissingBean(AdminClient.class)
    public AdminClient kafkaAdminClient(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
            final String bootstrapServers) {
        return AdminClient.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000",
            AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000"
        ));
    }

    // ── Inbound use cases ────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PublishEventUseCase.class)
    public PublishEventUseCase publishEventUseCase(
            final EventEnvelopeRepository envelopeRepo,
            final KafkaPublisherPort kafkaPublisher,
            final EventMetricsPort metrics) {
        return new EventPublisherService(envelopeRepo, kafkaPublisher, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(PublishEventBatchUseCase.class)
    public PublishEventBatchUseCase publishEventBatchUseCase(
            final EventEnvelopeRepository envelopeRepo,
            final KafkaPublisherPort kafkaPublisher,
            final EventMetricsPort metrics) {
        return new EventPublisherService(envelopeRepo, kafkaPublisher, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(QueryEventUseCase.class)
    public QueryEventUseCase queryEventUseCase(final EventEnvelopeRepository repo) {
        return new EventQueryService(repo);
    }

    @Bean
    @ConditionalOnMissingBean(QueryEventStatsUseCase.class)
    public QueryEventStatsUseCase queryEventStatsUseCase(
            final EventEnvelopeRepository envelopeRepo,
            final OutboxEntryRepository outboxRepo,
            final DeadLetterRepository dlqRepo,
            final EventSchemaRepository schemaRepo) {
        return new EventStatsService(envelopeRepo, outboxRepo, dlqRepo, schemaRepo);
    }

    @Bean
    @ConditionalOnMissingBean(ManageSchemaUseCase.class)
    public ManageSchemaUseCase manageSchemaUseCase(final EventSchemaRepository schemaRepo) {
        return new SchemaRegistryService(schemaRepo);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDeadLetterUseCase.class)
    public ProcessDeadLetterUseCase processDeadLetterUseCase(
            final DeadLetterRepository dlqRepo,
            final EventEnvelopeRepository envelopeRepo,
            final KafkaPublisherPort kafkaPublisher) {
        return new DeadLetterService(dlqRepo, envelopeRepo, kafkaPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(ReplayEventUseCase.class)
    public ReplayEventUseCase replayEventUseCase(
            final EventEnvelopeRepository envelopeRepo,
            final KafkaPublisherPort kafkaPublisher,
            final EventIdempotencyStorePort idempotencyStore) {
        return new ReplayEventService(envelopeRepo, kafkaPublisher, idempotencyStore);
    }

    // ── Outbox relay — reinstated 2026-11-26 (risk R2 of the Kafka audit) ─────
    // EventPublisherService still publishes directly; OutboxRelayService re-publishes what did not
    // end PUBLISHED (FAILED with exponential backoff, stale PENDING, expired RETRYING claims) and
    // declares DEAD (DLQ entry + ERROR log + metric) after max-attempts. It replaces the
    // OutboxPollerService (decommissioned 2026-09-18), which relayed an outbox_entries table that
    // nothing writes anymore; that class stays unwired.

    @Bean
    @ConditionalOnMissingBean(OutboxRelayService.class)
    @ConditionalOnProperty(prefix = "yow.event.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelayService outboxRelayService(
            final EventEnvelopeRepository envelopeRepo,
            final DeadLetterRepository dlqRepo,
            final KafkaPublisherPort kafkaPublisher,
            final BrokerAvailabilityPort broker,
            final EventMetricsPort metrics,
            final YowEventKernelProperties properties) {
        YowEventKernelProperties.Relay relay = properties.getOutbox().getRelay();
        long initialMs = relay.getInitialDelay().toMillis();
        RetryPolicy policy = new RetryPolicy(relay.getMaxAttempts(), initialMs, relay.getMultiplier(),
            Math.max(initialMs, relay.getMaxDelay().toMillis()));
        return new OutboxRelayService(envelopeRepo, dlqRepo, kafkaPublisher, broker, metrics, policy,
            relay.getBatchSize(), relay.getPendingGrace(), relay.getClaimTimeout());
    }

    // ── Consumer-side idempotency — 2026-11-26 ───────────────────────────────
    // Every listener container factory (each module's and Boot's default) gets the duplicate filter.

    @Bean
    public static IdempotentListenerContainerFactoryPostProcessor idempotentListenerContainerFactoryPostProcessor(
            final ObjectProvider<ProcessedEventStorePort> store,
            final ObjectProvider<EventMetricsPort> metrics,
            final ObjectProvider<YowEventKernelProperties> properties) {
        return new IdempotentListenerContainerFactoryPostProcessor(
            new IdempotentRecordInterceptor(store, metrics, properties));
    }

    // ── Consumer retries + common dead-letter topic tnt.dlq — lot L0 (decision G.8, 2026-11-26) ──
    // Every listener container factory without its own error handler retries a failing record
    // (tnt.kafka.retry.max-attempts, exponential 1 s x2) then publishes it to tnt.dlq.

    @Bean
    public static TntKafkaErrorHandlingPostProcessor tntKafkaErrorHandlingPostProcessor(
            final Environment environment,
            final ObjectProvider<MeterRegistry> meterRegistry) {
        return new TntKafkaErrorHandlingPostProcessor(environment, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessedEventPurgeService.class)
    @ConditionalOnProperty(prefix = "yow.event.consumer-idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProcessedEventPurgeService processedEventPurgeService(
            final ProcessedEventStorePort store,
            final YowEventKernelProperties properties) {
        return new ProcessedEventPurgeService(store, properties.getConsumerIdempotency().getRetention());
    }
}
