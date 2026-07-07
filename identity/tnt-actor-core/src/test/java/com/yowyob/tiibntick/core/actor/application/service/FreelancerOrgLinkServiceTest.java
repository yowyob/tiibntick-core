package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.LinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.command.UnlinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IFreelancerRepository;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerOrgLinkException;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerProfile;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerRole;
import com.yowyob.tiibntick.core.actor.domain.model.ServiceZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FreelancerOrgLinkService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class FreelancerOrgLinkServiceTest {

    @Mock
    private IFreelancerRepository repository;

    @Mock
    private IActorEventPublisher eventPublisher;

    private FreelancerOrgLinkService service;

    private UUID tenantId;
    private UUID actorId;
    private UUID orgId;
    private FreelancerProfile profile;

    @BeforeEach
    void setUp() {
        service = new FreelancerOrgLinkService(repository, eventPublisher);
        tenantId = UUID.randomUUID();
        actorId  = UUID.randomUUID();
        orgId    = UUID.randomUUID();
        profile  = FreelancerProfile.create(tenantId, actorId,
                List.of(ServiceZoneId.of(UUID.randomUUID())), List.of());
    }

    @Test
    @DisplayName("linkToFreelancerOrg(OWNER) links profile and publishes event")
    void linkToOrg_owner_success() {
        LinkFreelancerOrgCommand cmd = new LinkFreelancerOrgCommand(
                tenantId, actorId, orgId, FreelancerRole.OWNER, false);

        when(repository.findFirstByActorId(actorId)).thenReturn(Mono.just(profile));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishFreelancerOrgLinked(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.linkToFreelancerOrg(cmd))
                .expectNextMatches(p ->
                        orgId.equals(p.freelancerOrgId()) &&
                        p.roleInOrg() == FreelancerRole.OWNER)
                .verifyComplete();

        verify(eventPublisher).publishFreelancerOrgLinked(any());
    }

    @Test
    @DisplayName("linkToFreelancerOrg() is idempotent if already linked to same org + role")
    void linkToOrg_idempotent() {
        FreelancerProfile alreadyLinked = profile.withFreelancerOrgLink(
                orgId, FreelancerRole.OWNER, false);
        LinkFreelancerOrgCommand cmd = new LinkFreelancerOrgCommand(
                tenantId, actorId, orgId, FreelancerRole.OWNER, false);

        when(repository.findFirstByActorId(actorId)).thenReturn(Mono.just(alreadyLinked));

        StepVerifier.create(service.linkToFreelancerOrg(cmd))
                .expectNextMatches(p -> orgId.equals(p.freelancerOrgId()))
                .verifyComplete();

        // No save or event — idempotent no-op
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishFreelancerOrgLinked(any());
    }

    @Test
    @DisplayName("linkToFreelancerOrg() rejects linking to a different org without unlinking first")
    void linkToOrg_rejectsDifferentOrg() {
        UUID otherOrgId = UUID.randomUUID();
        FreelancerProfile alreadyLinked = profile.withFreelancerOrgLink(
                otherOrgId, FreelancerRole.OWNER, false);
        LinkFreelancerOrgCommand cmd = new LinkFreelancerOrgCommand(
                tenantId, actorId, orgId, FreelancerRole.SUB_DELIVERER, false);

        when(repository.findFirstByActorId(actorId)).thenReturn(Mono.just(alreadyLinked));

        StepVerifier.create(service.linkToFreelancerOrg(cmd))
                .expectErrorMatches(e -> e instanceof FreelancerOrgLinkException
                        && e.getMessage().contains("Unlink first"))
                .verify();
    }

    @Test
    @DisplayName("linkToFreelancerOrg() returns error when actor not found")
    void linkToOrg_actorNotFound() {
        LinkFreelancerOrgCommand cmd = new LinkFreelancerOrgCommand(
                tenantId, actorId, orgId, FreelancerRole.OWNER, false);

        when(repository.findFirstByActorId(actorId)).thenReturn(Mono.empty());

        StepVerifier.create(service.linkToFreelancerOrg(cmd))
                .expectError(FreelancerNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("unlinkFromFreelancerOrg() clears org link and publishes event")
    void unlinkFromOrg_success() {
        FreelancerProfile linked = profile.withFreelancerOrgLink(
                orgId, FreelancerRole.SUB_DELIVERER, true);
        UnlinkFreelancerOrgCommand cmd = new UnlinkFreelancerOrgCommand(
                tenantId, actorId, orgId);

        when(repository.findFirstByActorId(actorId)).thenReturn(Mono.just(linked));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishFreelancerOrgUnlinked(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.unlinkFromFreelancerOrg(cmd))
                .expectNextMatches(p -> !p.hasOrgLink())
                .verifyComplete();

        verify(eventPublisher).publishFreelancerOrgUnlinked(any());
    }

    @Test
    @DisplayName("unlinkFromFreelancerOrg() is no-op if not linked to specified org")
    void unlinkFromOrg_noOp() {
        UUID differentOrgId = UUID.randomUUID();
        FreelancerProfile linkedToOther = profile.withFreelancerOrgLink(
                differentOrgId, FreelancerRole.OWNER, false);
        UnlinkFreelancerOrgCommand cmd = new UnlinkFreelancerOrgCommand(
                tenantId, actorId, orgId);

        when(repository.findFirstByActorId(actorId)).thenReturn(Mono.just(linkedToOther));

        StepVerifier.create(service.unlinkFromFreelancerOrg(cmd))
                .expectNextMatches(p -> differentOrgId.equals(p.freelancerOrgId()))
                .verifyComplete();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("findSubDeliverersByOrg() delegates to repository")
    void findSubDeliverers_delegatesToRepo() {
        when(repository.findSubDeliverersByOrgId(orgId)).thenReturn(Flux.just(profile));

        StepVerifier.create(service.findSubDeliverersByOrg(orgId))
                .expectNext(profile)
                .verifyComplete();
    }

    @Test
    @DisplayName("findOwnerByOrg() delegates to repository")
    void findOwnerByOrg_delegatesToRepo() {
        FreelancerProfile owner = profile.withFreelancerOrgLink(orgId, FreelancerRole.OWNER, true);
        when(repository.findOwnerByOrgId(orgId)).thenReturn(Mono.just(owner));

        StepVerifier.create(service.findOwnerByOrg(orgId))
                .expectNextMatches(FreelancerProfile::isOrgOwner)
                .verifyComplete();
    }

    @Test
    @DisplayName("updateOrgVerificationStatus() delegates bulk update to repository")
    void updateOrgVerification_delegatesToRepo() {
        when(repository.updateOrgVerificationStatusForOrg(orgId, true))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.updateOrgVerificationStatus(orgId, true))
                .verifyComplete();

        verify(repository).updateOrgVerificationStatusForOrg(orgId, true);
    }
}
