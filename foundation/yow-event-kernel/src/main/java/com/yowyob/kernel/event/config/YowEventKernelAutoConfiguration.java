package com.yowyob.kernel.event.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
import com.yowyob.kernel.event.application.service.OutboxPollerService;
import com.yowyob.kernel.event.application.service.ReplayEventService;
import com.yowyob.kernel.event.application.service.SchemaRegistryService;
import com.yowyob.kernel.event.application.port.out.EventMetricsPort;
//import com.yowyob.kernel.event.config.YowEventKernelProperties;

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
 *   <li>{@code @ComponentScan} is scoped to {@code yowyob.kernel.event} to avoid
 *       accidentally picking up beans from the consuming application's classpath.</li>
 *   <li>{@code @EnableScheduling} activates the outbox poller scheduler.</li>
 * </ul>
 * @author MANFOUO Braun
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(YowEventKernelProperties.class)
@ComponentScan(basePackages = "yowyob.kernel.event")
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
            final OutboxEntryRepository outboxRepo) {
        return new EventPublisherService(envelopeRepo, outboxRepo);
    }

    @Bean
    @ConditionalOnMissingBean(PublishEventBatchUseCase.class)
    public PublishEventBatchUseCase publishEventBatchUseCase(
            final EventEnvelopeRepository envelopeRepo,
            final OutboxEntryRepository outboxRepo) {
        return new EventPublisherService(envelopeRepo, outboxRepo);
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

    // ── Outbox poller (scheduled) ────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(OutboxPollerService.class)
    public OutboxPollerService outboxPollerService(
            final EventEnvelopeRepository envelopeRepo,
            final OutboxEntryRepository outboxRepo,
            final DeadLetterRepository dlqRepo,
            final KafkaPublisherPort kafkaPublisher,
            final EventMetricsPort metrics) {
        return new OutboxPollerService(envelopeRepo, outboxRepo, dlqRepo, kafkaPublisher, metrics);
    }
}
