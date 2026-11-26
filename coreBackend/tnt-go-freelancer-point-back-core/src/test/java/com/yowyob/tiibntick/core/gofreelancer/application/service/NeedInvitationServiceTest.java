package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.NeedInvitationDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.NeedInvitationRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.NeedInvitation;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Lot C-52 — invitation rules (who, when, what happens). */
class NeedInvitationServiceTest {

    private final IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final DeliveryNeedApplicationService needService = mock(DeliveryNeedApplicationService.class);
    private final Map<UUID, NeedInvitation> store = new LinkedHashMap<>();
    private final NeedInvitationRepository invitations = new NeedInvitationRepository() {
        @Override public Mono<NeedInvitation> save(NeedInvitation i) {
            if (i.getId() == null) i.setId(UUID.randomUUID());
            store.put(i.getId(), i);
            return Mono.just(i);
        }
        @Override public Mono<NeedInvitation> findById(UUID id) { return Mono.justOrEmpty(store.get(id)); }
        @Override public Flux<NeedInvitation> findAllByDeliveryNeedId(UUID needId) {
            return Flux.fromIterable(new ArrayList<>(store.values())).filter(i -> needId.equals(i.getDeliveryNeedId()));
        }
        @Override public Flux<NeedInvitation> findAllByFreelancerIdAndStatus(UUID f, String s) {
            return Flux.fromIterable(new ArrayList<>(store.values()))
                    .filter(i -> f.equals(i.getFreelancerId()) && s.equals(i.getStatus()));
        }
        @Override public Flux<NeedInvitation> findAllByStatus(String s) {
            return Flux.fromIterable(new ArrayList<>(store.values())).filter(i -> s.equals(i.getStatus()));
        }
    };

    private final Instant t0 = Instant.parse("2026-11-19T10:00:00Z");
    private NeedInvitationService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID needId = UUID.randomUUID();
    private final UUID courierUser = UUID.randomUUID();
    private final UUID otherCourierUser = UUID.randomUUID();
    private GofpFreelancer courier;
    private GofpFreelancer otherCourier;
    private DeliveryNeed need;

    @BeforeEach
    void setUp() {
        service = new NeedInvitationService(needs, freelancers, invitations, needService);
        at(t0);
        courier = GofpFreelancer.builder().id(UUID.randomUUID()).coreUserId(courierUser).build();
        otherCourier = GofpFreelancer.builder().id(UUID.randomUUID()).coreUserId(otherCourierUser).build();
        need = DeliveryNeed.builder().id(needId).userId(ownerId).status(DeliveryNeedStatus.PENDING).build();
        when(needs.findById(needId)).thenReturn(Mono.just(need));
        when(freelancers.findByCoreUserId(courierUser)).thenReturn(Mono.just(courier));
        when(freelancers.findByCoreUserId(otherCourierUser)).thenReturn(Mono.just(otherCourier));
        when(freelancers.findById(courier.getId())).thenReturn(Mono.just(courier));
        when(freelancers.findByCoreUserId(ownerId)).thenReturn(Mono.empty());
        when(needService.view(any())).thenReturn(Mono.just(new DeliveryNeedResponseDTO()));
    }

    private void at(Instant now) {
        service.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    private void candidates(FreelancerCandidateDTO... list) {
        when(needService.getCandidatesWithPricing(needId, ownerId)).thenReturn(Flux.just(list));
    }

    private FreelancerCandidateDTO candidate(GofpFreelancer f, String status) {
        return FreelancerCandidateDTO.builder().freelancerId(f.getId()).estimatedPrice(2500.0).status(status).build();
    }

    private static boolean status(Throwable e, int code) {
        return e instanceof ResponseStatusException r && r.getStatusCode().value() == code;
    }

    private NeedInvitationDTO inviteOk() {
        candidates(candidate(courier, "AVAILABLE"));
        return service.invite(needId, courier.getId(), ownerId).block();
    }

    @Test
    @DisplayName("inviter un livreur disponible : invitation ouverte 10 min, au prix du candidat")
    void invite_available_opensForTenMinutes() {
        NeedInvitationDTO dto = inviteOk();
        assertThat(dto.getStatus()).isEqualTo("OPEN");
        assertThat(dto.getExpiresAt()).isEqualTo(t0.plus(Duration.ofMinutes(10)));
        assertThat(dto.getRemainingSeconds()).isEqualTo(600);
        assertThat(dto.getEstimatedPrice()).isEqualTo(2500.0);
        verify(needService, never()).completeAssignment(any(), any());
    }

    @Test
    @DisplayName("livreur en course (BUSY) → 409, aucune invitation")
    void invite_busy_conflict() {
        candidates(candidate(courier, "BUSY"));
        StepVerifier.create(service.invite(needId, courier.getId(), ownerId))
                .expectErrorMatches(e -> status(e, 409)).verify();
        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("livreur absent des candidats (en pause, hors zone) → 409")
    void invite_notCandidate_conflict() {
        candidates(candidate(otherCourier, "AVAILABLE"));
        StepVerifier.create(service.invite(needId, courier.getId(), ownerId))
                .expectErrorMatches(e -> status(e, 409)).verify();
        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("non-propriétaire du besoin → 403 (garde des candidats)")
    void invite_notOwner_forbidden() {
        UUID stranger = UUID.randomUUID();
        when(needService.getCandidatesWithPricing(needId, stranger))
                .thenReturn(Flux.error(new AccessDeniedException("not yours")));
        StepVerifier.create(service.invite(needId, courier.getId(), stranger))
                .expectError(AccessDeniedException.class).verify();
        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("une seule invitation ouverte par besoin ; après expiration on peut en envoyer une autre")
    void invite_oneOpenPerNeed() {
        inviteOk();
        candidates(candidate(courier, "AVAILABLE"), candidate(otherCourier, "AVAILABLE"));
        StepVerifier.create(service.invite(needId, otherCourier.getId(), ownerId))
                .expectErrorMatches(e -> status(e, 409)).verify();
        at(t0.plus(Duration.ofMinutes(11)));
        NeedInvitationDTO second = service.invite(needId, otherCourier.getId(), ownerId).block();
        assertThat(second.getStatus()).isEqualTo("OPEN");
        assertThat(store.values()).extracting(NeedInvitation::getStatus).containsExactly("EXPIRED", "OPEN");
    }

    @Test
    @DisplayName("besoin déjà attribué → 409")
    void invite_assignedNeed_conflict() {
        need.setStatus(DeliveryNeedStatus.ASSIGNED);
        candidates(candidate(courier, "AVAILABLE"));
        StepVerifier.create(service.invite(needId, courier.getId(), ownerId))
                .expectErrorMatches(e -> status(e, 409)).verify();
    }

    @Test
    @DisplayName("le livreur invité accepte → chemin d'élection (completeAssignment), invitation ACCEPTED")
    void accept_runsElectionPath() {
        NeedInvitationDTO inv = inviteOk();
        DeliveryNeedResponseDTO assigned = new DeliveryNeedResponseDTO();
        assigned.setStatus(DeliveryNeedStatus.ASSIGNED);
        when(needService.completeAssignment(need, courier)).thenReturn(Mono.just(assigned));

        StepVerifier.create(service.accept(inv.getId(), courierUser)).expectNext(assigned).verifyComplete();

        verify(needService).completeAssignment(need, courier);
        assertThat(store.get(inv.getId()).getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("acceptation après les 10 min → 410, aucune livraison, invitation EXPIRED")
    void accept_afterExpiry_gone() {
        NeedInvitationDTO inv = inviteOk();
        at(t0.plus(Duration.ofMinutes(10)));
        StepVerifier.create(service.accept(inv.getId(), courierUser))
                .expectErrorMatches(e -> status(e, 410)).verify();
        verify(needService, never()).completeAssignment(any(), any());
        assertThat(store.get(inv.getId()).getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("un autre livreur ne peut ni accepter ni refuser l'invitation d'un autre → 403")
    void accept_byOtherCourier_forbidden() {
        NeedInvitationDTO inv = inviteOk();
        StepVerifier.create(service.accept(inv.getId(), otherCourierUser))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(service.decline(inv.getId(), otherCourierUser, null))
                .expectError(AccessDeniedException.class).verify();
        StepVerifier.create(service.accept(inv.getId(), ownerId))
                .expectError(AccessDeniedException.class).verify();
        verify(needService, never()).completeAssignment(any(), any());
        assertThat(store.get(inv.getId()).getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("refus → DECLINED avec motif ; le besoin n'est pas touché ; accepter ensuite → 409")
    void decline_keepsNeedOpen() {
        NeedInvitationDTO inv = inviteOk();
        NeedInvitationDTO out = service.decline(inv.getId(), courierUser, "  trop loin  ").block();
        assertThat(out.getStatus()).isEqualTo("DECLINED");
        assertThat(out.getDeclineReason()).isEqualTo("trop loin");
        assertThat(need.getStatus()).isEqualTo(DeliveryNeedStatus.PENDING);
        verify(needs, never()).save(any());
        StepVerifier.create(service.accept(inv.getId(), courierUser))
                .expectErrorMatches(e -> status(e, 409)).verify();
    }

    // ── Lot C-36 — notifications in-app ─────────────────────────────────

    @Test
    @DisplayName("C-36 : invitation → le livreur est notifié ; refus → l'expéditeur ; une seule fois chacun")
    void inApp_inviteThenDecline() {
        InAppEvents events = mock(InAppEvents.class);
        when(events.courseOffered(any())).thenReturn(Mono.empty());
        when(events.invitationClosed(any())).thenReturn(Mono.empty());
        service.setInAppEvents(events);
        NeedInvitationDTO inv = inviteOk();
        verify(events, org.mockito.Mockito.times(1)).courseOffered(org.mockito.ArgumentMatchers.argThat(
                i -> i.getId().equals(inv.getId()) && courier.getId().equals(i.getFreelancerId())));
        service.decline(inv.getId(), courierUser, null).block();
        verify(events, org.mockito.Mockito.times(1)).invitationClosed(org.mockito.ArgumentMatchers.argThat(
                i -> "DECLINED".equals(i.getStatus())));
    }

    @Test
    @DisplayName("C-36 : une invitation sans réponse expire au balayage, l'expéditeur le sait ; rejouer ne refait rien")
    void inApp_expirySweep() {
        InAppEvents events = mock(InAppEvents.class);
        when(events.courseOffered(any())).thenReturn(Mono.empty());
        when(events.invitationClosed(any())).thenReturn(Mono.empty());
        service.setInAppEvents(events);
        NeedInvitationDTO inv = inviteOk();
        assertThat(service.expireOverdue().block()).isZero();
        at(t0.plus(Duration.ofMinutes(11)));
        assertThat(service.expireOverdue().block()).isEqualTo(1);
        assertThat(service.expireOverdue().block()).isZero();
        assertThat(store.get(inv.getId()).getStatus()).isEqualTo("EXPIRED");
        verify(events, org.mockito.Mockito.times(1)).invitationClosed(org.mockito.ArgumentMatchers.argThat(
                i -> "EXPIRED".equals(i.getStatus())));
    }

    @Test
    @DisplayName("mes invitations : ouvertes et non expirées seulement, avec le besoin")
    void mine_onlyOpenUnexpired() {
        inviteOk();
        assertThat(service.mine(courierUser).collectList().block()).hasSize(1)
                .allSatisfy(d -> assertThat(d.getNeed()).isNotNull());
        assertThat(service.mine(otherCourierUser).collectList().block()).isEmpty();
        at(t0.plus(Duration.ofMinutes(10)));
        assertThat(service.mine(courierUser).collectList().block()).isEmpty();
    }

    @Test
    @DisplayName("vue expéditeur : une invitation ouverte passée l'échéance se lit EXPIRED")
    void forNeed_readsExpired() {
        inviteOk();
        when(needService.getDeliveryNeed(eq(needId), eq(ownerId))).thenReturn(Mono.just(new DeliveryNeedResponseDTO()));
        at(t0.plus(Duration.ofMinutes(12)));
        assertThat(service.forNeed(needId, ownerId).collectList().block())
                .extracting(NeedInvitationDTO::getStatus).containsExactly("EXPIRED");
    }
}
