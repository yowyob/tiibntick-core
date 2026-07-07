package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.messaging;

import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgEventPublisherPort;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgCreatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgSuspendedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgVerifiedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.KycLevelUpgradedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.SubDelivererAssociatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.SubDelivererRevokedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

/**
 * Outbound messaging adapter implementing {@link FreelancerOrgEventPublisherPort}.
 *
 * <p>In the current implementation, events are published via Spring's
 * {@link ApplicationEventPublisher}. The {@code tnt-bootstrap} module wires
 * an application event listener that forwards events to Kafka via
 * {@code yow-event-kernel} when the messaging infrastructure is available.
 *
 * <p>This design keeps the domain and application layers decoupled from
 * Kafka-specific dependencies, and allows in-process testing without a Kafka broker.
 *
 * <p>Kafka topics targeted (published by bootstrap listener):
 * <ul>
 *   <li>{@code tnt.freelancer_org.created}</li>
 *   <li>{@code tnt.freelancer_org.verified}</li>
 *   <li>{@code tnt.freelancer_org.suspended}</li>
 *   <li>{@code tnt.freelancer_org.kyc.upgraded}</li>
 *   <li>{@code tnt.freelancer_org.sub_deliverer.associated}</li>
 *   <li>{@code tnt.freelancer_org.sub_deliverer.revoked}</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public class FreelancerOrgEventPublisherAdapter implements FreelancerOrgEventPublisherPort {

    private static final Logger log =
            LoggerFactory.getLogger(FreelancerOrgEventPublisherAdapter.class);

    private final ApplicationEventPublisher springEventPublisher;

    /**
     * Constructor injection.
     *
     * @param springEventPublisher Spring application event bus
     */
    public FreelancerOrgEventPublisherAdapter(ApplicationEventPublisher springEventPublisher) {
        this.springEventPublisher = springEventPublisher;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgCreated(FreelancerOrgCreatedEvent event) {
        return Mono.fromRunnable(() -> {
            log.info("Publishing FreelancerOrgCreatedEvent: orgId={}, tenantId={}",
                    event.orgId(), event.tenantId());
            springEventPublisher.publishEvent(event);
        });
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgVerified(FreelancerOrgVerifiedEvent event) {
        return Mono.fromRunnable(() -> {
            log.info("Publishing FreelancerOrgVerifiedEvent: orgId={}, kycLevel={}",
                    event.orgId(), event.kycLevel());
            springEventPublisher.publishEvent(event);
        });
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishFreelancerOrgSuspended(FreelancerOrgSuspendedEvent event) {
        return Mono.fromRunnable(() -> {
            log.warn("Publishing FreelancerOrgSuspendedEvent: orgId={}, reason={}",
                    event.orgId(), event.reason());
            springEventPublisher.publishEvent(event);
        });
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishSubDelivererAssociated(SubDelivererAssociatedEvent event) {
        return Mono.fromRunnable(() -> {
            log.info("Publishing SubDelivererAssociatedEvent: orgId={}, subDeliverer={}",
                    event.orgId(), event.subDelivererId());
            springEventPublisher.publishEvent(event);
        });
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishSubDelivererRevoked(SubDelivererRevokedEvent event) {
        return Mono.fromRunnable(() -> {
            log.info("Publishing SubDelivererRevokedEvent: orgId={}, subDeliverer={}",
                    event.orgId(), event.subDelivererId());
            springEventPublisher.publishEvent(event);
        });
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> publishKycLevelUpgraded(KycLevelUpgradedEvent event) {
        return Mono.fromRunnable(() -> {
            log.info("Publishing KycLevelUpgradedEvent: orgId={}, {} -> {}",
                    event.orgId(), event.previousLevel(), event.newLevel());
            springEventPublisher.publishEvent(event);
        });
    }
}
