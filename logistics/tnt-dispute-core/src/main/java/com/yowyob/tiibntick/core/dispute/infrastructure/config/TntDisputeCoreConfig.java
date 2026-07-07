package com.yowyob.tiibntick.core.dispute.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IEvidenceUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IMediationUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.*;
import com.yowyob.tiibntick.core.dispute.application.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring configuration for the tnt-dispute-core module.
 *
 * <p>Wires application services (use case implementations) with their outbound port adapters.
 * All service beans are defined here to maintain explicit dependency visibility and
 * facilitate testing with mock adapters.
 *
 * <p>Scheduling is enabled for the SLA watchdog ({@link DisputeSLAScheduler}).
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableScheduling
public class TntDisputeCoreConfig {

    // =========================================================================
    // JACKSON — shared ObjectMapper with Java Time support
    // =========================================================================

    @Bean
    public ObjectMapper disputeObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // =========================================================================
    // APPLICATION SERVICES — Use Case implementations
    // =========================================================================

    @Bean
    @ConditionalOnMissingBean(IDisputeCommandUseCase.class)
    public IDisputeCommandUseCase disputeCommandUseCase(
            IDisputeRepository repository,
            IDisputeEventPublisher eventPublisher,
            IDisputeNotificationPort notificationPort,
            IDeliveryStatusPort deliveryStatusPort,
            IBillingCompensationPort billingCompensationPort,
            IBlockchainProofPort blockchainProofPort) {
        return new DisputeCommandService(
                repository, eventPublisher, notificationPort,
                deliveryStatusPort, billingCompensationPort, blockchainProofPort);
    }

    @Bean
    @ConditionalOnMissingBean(IDisputeQueryUseCase.class)
    public IDisputeQueryUseCase disputeQueryUseCase(IDisputeRepository repository) {
        return new DisputeQueryService(repository);
    }

    @Bean
    @ConditionalOnMissingBean(IEvidenceUseCase.class)
    public IEvidenceUseCase evidenceUseCase(
            IDisputeRepository repository,
            IDisputeEventPublisher eventPublisher,
            IBlockchainProofPort blockchainProofPort) {
        return new EvidenceApplicationService(repository, eventPublisher, blockchainProofPort);
    }
}
