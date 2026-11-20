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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Lot C-52 — a client invites one courier to an open delivery need; the courier accepts or declines.
 *
 * <ul>
 *   <li>Invite: owner of the need only (403); the courier must be among the need's current candidates
 *       (paused or out of zone → 409) and not busy (409); one open invitation per need (409).</li>
 *   <li>Accept: the invited courier only (403); past the deadline → 410; then exactly the election path
 *       of a direct assignment ({@link DeliveryNeedApplicationService#completeAssignment}): server price,
 *       payable delivery linked to the need, payment mode of the need.</li>
 *   <li>Decline or expiry: the invitation closes; the need and its announcement stay open for others.</li>
 * </ul>
 * Expiry is checked on read and on answer; no scheduled job.
 */
@Slf4j
@Service
public class NeedInvitationService {

    public static final Duration TTL = Duration.ofMinutes(10);

    private final IDeliveryNeedRepository needs;
    private final GofpFreelancerRepository freelancers;
    private final NeedInvitationRepository invitations;
    private final DeliveryNeedApplicationService needService;
    private Clock clock = Clock.systemUTC();

    public NeedInvitationService(IDeliveryNeedRepository needs, GofpFreelancerRepository freelancers,
                                 NeedInvitationRepository invitations, DeliveryNeedApplicationService needService) {
        this.needs = needs;
        this.freelancers = freelancers;
        this.invitations = invitations;
        this.needService = needService;
    }

    /** Tests only. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ── client side ──────────────────────────────────────────────────────

    public Mono<NeedInvitationDTO> invite(UUID needId, UUID freelancerId, UUID callerId) {
        if (freelancerId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "freelancerId is required"));
        }
        // getCandidatesWithPricing loads the need (404) and checks its owner (403) first.
        return needService.getCandidatesWithPricing(needId, callerId).collectList()
                .flatMap(candidates -> needs.findById(needId).flatMap(need -> {
                    if (need.getStatus() != null && need.getStatus() != DeliveryNeedStatus.PENDING) {
                        return Mono.error(conflict("This delivery need already has a courier"));
                    }
                    FreelancerCandidateDTO candidate = candidates.stream()
                            .filter(c -> freelancerId.equals(c.getFreelancerId()))
                            .findFirst().orElse(null);
                    if (candidate == null) {
                        return Mono.error(conflict("This courier is not available for this delivery now"));
                    }
                    if (FreelancerCandidateDTO.BUSY.equals(candidate.getStatus())) {
                        return Mono.error(conflict("This courier is on another delivery"));
                    }
                    return closeExpiredAndCheckNoneOpen(needId).then(Mono.defer(() -> {
                        Instant now = clock.instant();
                        NeedInvitation inv = NeedInvitation.builder()
                                .deliveryNeedId(needId)
                                .freelancerId(freelancerId)
                                .invitedBy(need.getUserId())
                                .status(NeedInvitation.OPEN)
                                .estimatedPrice(candidate.getEstimatedPrice())
                                .expiresAt(now.plus(TTL))
                                .createdAt(now)
                                .build();
                        return invitations.save(inv)
                                .onErrorMap(org.springframework.dao.DataIntegrityViolationException.class,
                                        e -> conflict("An invitation is already pending for this delivery"));
                    }));
                }))
                .map(inv -> toDto(inv, null));
    }

    /** Invitations of a need, most recent first — owner only. */
    public Flux<NeedInvitationDTO> forNeed(UUID needId, UUID callerId) {
        return needService.getDeliveryNeed(needId, callerId)
                .flatMapMany(need -> invitations.findAllByDeliveryNeedId(needId))
                .map(inv -> toDto(inv, null));
    }

    // ── courier side ─────────────────────────────────────────────────────

    /** Open, unexpired invitations of the caller's courier profile (none without a profile). */
    public Flux<NeedInvitationDTO> mine(UUID callerId) {
        if (callerId == null) {
            return Flux.error(new AccessDeniedException("An authenticated courier is required"));
        }
        Instant now = clock.instant();
        return freelancers.findByCoreUserId(callerId)
                .flatMapMany(me -> invitations.findAllByFreelancerIdAndStatus(me.getId(), NeedInvitation.OPEN))
                .filter(inv -> !inv.isExpiredAt(now))
                .concatMap(inv -> needs.findById(inv.getDeliveryNeedId())
                        .flatMap(needService::view)
                        .map(view -> toDto(inv, view))
                        .defaultIfEmpty(toDto(inv, null)));
    }

    public Mono<DeliveryNeedResponseDTO> accept(UUID invitationId, UUID callerId) {
        return openInvitationOfCaller(invitationId, callerId)
                .flatMap(inv -> needs.findById(inv.getDeliveryNeedId())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "DeliveryNeed not found: " + inv.getDeliveryNeedId())))
                        .flatMap(need -> {
                            if (need.getStatus() != null && need.getStatus() != DeliveryNeedStatus.PENDING) {
                                return close(inv, NeedInvitation.EXPIRED, null)
                                        .then(Mono.<DeliveryNeedResponseDTO>error(
                                                conflict("This delivery already has a courier")));
                            }
                            return freelancers.findById(inv.getFreelancerId())
                                    .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                            "Freelancer not found: " + inv.getFreelancerId())))
                                    .flatMap(courier -> needService.completeAssignment(need, courier))
                                    .flatMap(result -> close(inv, NeedInvitation.ACCEPTED, null).thenReturn(result));
                        }));
    }

    public Mono<NeedInvitationDTO> decline(UUID invitationId, UUID callerId, String reason) {
        String clean = reason == null || reason.isBlank() ? null
                : reason.strip().substring(0, Math.min(500, reason.strip().length()));
        return openInvitationOfCaller(invitationId, callerId)
                .flatMap(inv -> close(inv, NeedInvitation.DECLINED, clean))
                .map(inv -> toDto(inv, null));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** 404 unknown → 403 not the invited courier → 409 already answered → 410 expired (then closed). */
    private Mono<NeedInvitation> openInvitationOfCaller(UUID invitationId, UUID callerId) {
        if (callerId == null) {
            return Mono.error(new AccessDeniedException("An authenticated courier is required"));
        }
        return invitations.findById(invitationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invitation not found: " + invitationId)))
                .flatMap(inv -> freelancers.findByCoreUserId(callerId)
                        .filter(me -> me.getId().equals(inv.getFreelancerId()))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "This invitation is addressed to another courier")))
                        .thenReturn(inv))
                .flatMap(inv -> {
                    if (inv.isExpiredAt(clock.instant())) {
                        return close(inv, NeedInvitation.EXPIRED, null)
                                .then(Mono.error(new ResponseStatusException(HttpStatus.GONE,
                                        "This invitation has expired")));
                    }
                    if (!NeedInvitation.OPEN.equals(inv.getStatus())) {
                        return Mono.error(conflict("This invitation was already answered"));
                    }
                    return Mono.just(inv);
                });
    }

    private Mono<Void> closeExpiredAndCheckNoneOpen(UUID needId) {
        Instant now = clock.instant();
        return invitations.findAllByDeliveryNeedId(needId)
                .filter(inv -> NeedInvitation.OPEN.equals(inv.getStatus()))
                .concatMap(inv -> inv.isExpiredAt(now)
                        ? close(inv, NeedInvitation.EXPIRED, null).then(Mono.<NeedInvitation>empty())
                        : Mono.just(inv))
                .hasElements()
                .flatMap(stillOpen -> stillOpen
                        ? Mono.error(conflict("An invitation is already pending for this delivery"))
                        : Mono.empty());
    }

    private Mono<NeedInvitation> close(NeedInvitation inv, String status, String reason) {
        inv.setStatus(status);
        inv.setDeclineReason(reason);
        inv.setRespondedAt(clock.instant());
        return invitations.save(inv);
    }

    private NeedInvitationDTO toDto(NeedInvitation inv, DeliveryNeedResponseDTO need) {
        Instant now = clock.instant();
        String status = inv.effectiveStatus(now);
        long remaining = NeedInvitation.OPEN.equals(status) && inv.getExpiresAt() != null
                ? Math.max(0, Duration.between(now, inv.getExpiresAt()).getSeconds()) : 0;
        return NeedInvitationDTO.builder()
                .id(inv.getId())
                .deliveryNeedId(inv.getDeliveryNeedId())
                .freelancerId(inv.getFreelancerId())
                .status(status)
                .expiresAt(inv.getExpiresAt())
                .remainingSeconds(remaining)
                .estimatedPrice(inv.getEstimatedPrice())
                .declineReason(inv.getDeclineReason())
                .createdAt(inv.getCreatedAt())
                .respondedAt(inv.getRespondedAt())
                .need(need)
                .build();
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
