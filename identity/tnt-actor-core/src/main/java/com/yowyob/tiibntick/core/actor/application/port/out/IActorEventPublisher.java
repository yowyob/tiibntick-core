package com.yowyob.tiibntick.core.actor.application.port.out;

import com.yowyob.tiibntick.core.actor.domain.event.ActorLocationUpdatedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.ActorStatusChangedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.BadgeEarnedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.DelivererMissionAssignedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerAssociatedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerDissociatedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerOrgLinkedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerOrgUnlinkedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.KycValidatedEvent;
import reactor.core.publisher.Mono;

/**
 * Outbound port — actor domain event publishing.
 *
 * <h3> additions — FreelancerOrganization integration</h3>
 * <ul>
 *   <li>{@link #publishFreelancerOrgLinked} — emits after a FreelancerProfile is
 *       linked to a FreelancerOrganization.</li>
 *   <li>{@link #publishFreelancerOrgUnlinked} — emits after a FreelancerProfile's
 *       org link is removed.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public interface IActorEventPublisher {

    Mono<Void> publishActorStatusChanged(ActorStatusChangedEvent event);

    Mono<Void> publishLocationUpdated(ActorLocationUpdatedEvent event);

    Mono<Void> publishBadgeEarned(BadgeEarnedEvent event);

    Mono<Void> publishFreelancerAssociated(FreelancerAssociatedEvent event);

    Mono<Void> publishFreelancerDissociated(FreelancerDissociatedEvent event);

    Mono<Void> publishKycValidated(KycValidatedEvent event);

    Mono<Void> publishMissionAssigned(DelivererMissionAssignedEvent event);

    // FreelancerOrganization integration ─────────────────────────────

    /**
     * Publishes a {@link FreelancerOrgLinkedEvent} when a FreelancerProfile is
     * linked to a FreelancerOrganization.
     *
     * @param event the domain event
     * @return empty Mono on success
     */
    Mono<Void> publishFreelancerOrgLinked(FreelancerOrgLinkedEvent event);

    /**
     * Publishes a {@link FreelancerOrgUnlinkedEvent} when a FreelancerProfile's
     * FreelancerOrganization link is removed.
     *
     * @param event the domain event
     * @return empty Mono on success
     */
    Mono<Void> publishFreelancerOrgUnlinked(FreelancerOrgUnlinkedEvent event);
}
