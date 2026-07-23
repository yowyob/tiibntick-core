package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.LinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.command.UnlinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindFreelancerByOrgUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.ILinkFreelancerOrgUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerOrgLinkedEvent;
import com.yowyob.tiibntick.core.actor.domain.event.FreelancerOrgUnlinkedEvent;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerOrgLinkException;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link ILinkFreelancerOrgUseCase} and
 * {@link IFindFreelancerByOrgUseCase}.
 *
 * <p>Orchestrates FreelancerOrganization link operations on actor profiles:
 * <ol>
 *   <li>Validates the actor exists in the given tenant.</li>
 *   <li>Applies the domain mutation (withFreelancerOrgLink / withoutFreelancerOrg).</li>
 *   <li>Persists via {@link IFreelancerRepository}.</li>
 *   <li>Publishes the domain event via {@link IActorEventPublisher}.</li>
 * </ol>
 *
 * <p>This service is primarily driven by the {@code FreelancerOrgEventConsumer}
 * (Kafka adapter) which converts org-level events from {@code tnt-organization-core}
 * into actor profile mutations.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
public class FreelancerOrgLinkService
        implements ILinkFreelancerOrgUseCase, IFindFreelancerByOrgUseCase {

    private final IFreelancerRepository freelancerRepository;
    private final IActorEventPublisher eventPublisher;

    public FreelancerOrgLinkService(IFreelancerRepository freelancerRepository,
                                     IActorEventPublisher eventPublisher) {
        this.freelancerRepository = freelancerRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── ILinkFreelancerOrgUseCase ──────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>If the actor is already linked to the same org with the same role,
     * the operation is idempotent (no-op, returns current state).
     * If linked to a <em>different</em> org, throws {@link FreelancerOrgLinkException}.
     */
    @Override
    @Transactional
    public Mono<FreelancerProfile> linkToFreelancerOrg(LinkFreelancerOrgCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return freelancerRepository.findFirstByActorId(command.actorId())
                .switchIfEmpty(Mono.error(new FreelancerNotFoundException(
                        command.tenantId(), command.actorId())))
                .flatMap(profile -> {
                    // Idempotency check — already linked to the same org
                    if (command.freelancerOrgId().equals(profile.freelancerOrgId())
                            && command.role() == profile.roleInOrg()) {
                        log.debug("linkToFreelancerOrg: actor {} already linked to org {} as {} — no-op",
                                command.actorId(), command.freelancerOrgId(), command.role());
                        return Mono.just(profile);
                    }
                    // Guard: cannot link to a different org without unlinking first
                    if (profile.hasOrgLink()
                            && !command.freelancerOrgId().equals(profile.freelancerOrgId())) {
                        return Mono.error(new FreelancerOrgLinkException(
                                command.actorId(), command.freelancerOrgId(),
                                "Actor " + command.actorId()
                                        + " is already linked to org " + profile.freelancerOrgId()
                                        + ". Unlink first before joining another org."));
                    }
                    // Apply domain mutation and persist
                    FreelancerProfile updated = profile.withFreelancerOrgLink(
                            command.freelancerOrgId(), command.role(), command.isOrgVerified());
                    return freelancerRepository.save(updated)
                            .flatMap(saved -> {
                                FreelancerOrgLinkedEvent event = FreelancerOrgLinkedEvent.of(
                                        saved.actorId(), saved.tenantId(),
                                        command.freelancerOrgId(), command.role());
                                return eventPublisher.publishFreelancerOrgLinked(event)
                                        .thenReturn(saved);
                            });
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the actor is not linked to the specified org, the operation is a no-op.
     */
    @Override
    @Transactional
    public Mono<FreelancerProfile> unlinkFromFreelancerOrg(UnlinkFreelancerOrgCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return freelancerRepository.findFirstByActorId(command.actorId())
                .switchIfEmpty(Mono.error(new FreelancerNotFoundException(
                        command.tenantId(), command.actorId())))
                .flatMap(profile -> {
                    // No-op if not linked to the specified org
                    if (!command.freelancerOrgId().equals(profile.freelancerOrgId())) {
                        log.debug("unlinkFromFreelancerOrg: actor {} not linked to org {} — no-op",
                                command.actorId(), command.freelancerOrgId());
                        return Mono.just(profile);
                    }
                    FreelancerProfile updated = profile.withoutFreelancerOrg();
                    return freelancerRepository.save(updated)
                            .flatMap(saved -> {
                                FreelancerOrgUnlinkedEvent event = FreelancerOrgUnlinkedEvent.of(
                                        saved.actorId(), saved.tenantId(), command.freelancerOrgId());
                                return eventPublisher.publishFreelancerOrgUnlinked(event)
                                        .thenReturn(saved);
                            });
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to a bulk SQL update for efficiency — does not load individual profiles.
     */
    @Override
    public Mono<Void> updateOrgVerificationStatus(UUID orgId, boolean verified) {
        Objects.requireNonNull(orgId, "orgId must not be null");
        return freelancerRepository.updateOrgVerificationStatusForOrg(orgId, verified)
                .doOnSuccess(v -> log.info(
                        "Updated isOrgVerified={} for all profiles linked to org {}",
                        verified, orgId));
    }

    // ── IFindFreelancerByOrgUseCase ────────────────────────────────────────────

    @Override
    public Flux<FreelancerProfile> findSubDeliverersByOrg(UUID orgId) {
        Objects.requireNonNull(orgId, "orgId must not be null");
        return freelancerRepository.findSubDeliverersByOrgId(orgId);
    }

    @Override
    public Mono<FreelancerProfile> findOwnerByOrg(UUID orgId) {
        Objects.requireNonNull(orgId, "orgId must not be null");
        return freelancerRepository.findOwnerByOrgId(orgId);
    }

    @Override
    public Mono<FreelancerProfile> findByActorIdAndOrg(UUID actorId, UUID orgId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(orgId, "orgId must not be null");
        return freelancerRepository.findByActorIdAndOrgId(actorId, orgId);
    }
}
