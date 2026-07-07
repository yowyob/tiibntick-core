package com.yowyob.tiibntick.core.organization.application.port.out;

import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgCreatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgSuspendedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgVerifiedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.KycLevelUpgradedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.SubDelivererAssociatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.SubDelivererRevokedEvent;
import reactor.core.publisher.Mono;

/**
 * Outbound event publishing port for FreelancerOrganization domain events.
 *
 * <p>In production, the adapter implementation publishes events to Kafka via
 * {@code yow-event-kernel}. In tests, a no-op or in-memory adapter is used.
 *
 * <p>This is a secondary port (driven port) in the hexagonal architecture.
 *
 * @author MANFOUO Braun
 */
public interface FreelancerOrgEventPublisherPort {

    /**
     * Publishes a {@link FreelancerOrgCreatedEvent} to the event bus.
     *
     * @param event the event to publish
     * @return a {@link Mono} completing when the event is published
     */
    Mono<Void> publishFreelancerOrgCreated(FreelancerOrgCreatedEvent event);

    /**
     * Publishes a {@link FreelancerOrgVerifiedEvent} to the event bus.
     *
     * @param event the event to publish
     * @return a {@link Mono} completing when the event is published
     */
    Mono<Void> publishFreelancerOrgVerified(FreelancerOrgVerifiedEvent event);

    /**
     * Publishes a {@link FreelancerOrgSuspendedEvent} to the event bus.
     *
     * @param event the event to publish
     * @return a {@link Mono} completing when the event is published
     */
    Mono<Void> publishFreelancerOrgSuspended(FreelancerOrgSuspendedEvent event);

    /**
     * Publishes a {@link SubDelivererAssociatedEvent} to the event bus.
     *
     * @param event the event to publish
     * @return a {@link Mono} completing when the event is published
     */
    Mono<Void> publishSubDelivererAssociated(SubDelivererAssociatedEvent event);

    /**
     * Publishes a {@link SubDelivererRevokedEvent} to the event bus.
     *
     * @param event the event to publish
     * @return a {@link Mono} completing when the event is published
     */
    Mono<Void> publishSubDelivererRevoked(SubDelivererRevokedEvent event);

    /**
     * Publishes a {@link KycLevelUpgradedEvent} to the event bus.
     *
     * @param event the event to publish
     * @return a {@link Mono} completing when the event is published
     */
    Mono<Void> publishKycLevelUpgraded(KycLevelUpgradedEvent event);
}
