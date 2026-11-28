package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRevocationListener;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Go-Freelancer side of a relay revocation (decision D-129). Told by the core when a relay hub is revoked
 * ({@link IRelayHubRevocationListener}), and used directly for a Go relay point that has no hub in the core yet.
 *
 * <p>Effects on a Go-Freelancer relay point: local status {@code REVOKED}, inactive; actor-core relay operator
 * profile deactivated; the operator notified in the language of their account (template
 * {@value GofpNotificationTemplates#RELAY_STATUS_REVOKED_REASON}, parameter {@code reason}) and by e-mail.
 * A hub that is not a Go-Freelancer relay point (agency hub) is ignored here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpRelayRevocationApplier implements IRelayHubRevocationListener {

    /** Reactor context key carrying the login URL of the administrator's request (e-mail link), optional. */
    public static final String LOGIN_URL_CONTEXT_KEY = "gofp.relay.revoke.loginUrl";

    private final GofpRelayPointRepository relayPoints;
    private final IRelayOperatorRepository relayOperators;
    private final GofpFreelancerRepository freelancers;
    private final PushNotificationPort push;
    private final EmailPort email;

    @Override
    public Mono<Void> onRevoked(RelayHubRevocation revocation) {
        RelayHub hub = revocation.hub();
        return relayPoints.findByCoreRelayPointId(hub.id())
                .flatMap(rp -> Mono.deferContextual(ctx -> apply(rp, hub.tenantId(), hub.revocationReason(),
                        ctx.<String>getOrDefault(LOGIN_URL_CONTEXT_KEY, null))))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("Revoked hub {} is not a Go-Freelancer relay point: nothing to align", hub.id())))
                .then();
    }

    /**
     * Applies the revocation to the Go relay point: status, operator profile, notifications.
     *
     * @param tenantId tenant of the hub (the system tenant for Go relay points)
     */
    public Mono<GofpRelayPoint> apply(GofpRelayPoint rp, UUID tenantId, String reason, String loginUrl) {
        rp.setStatus(RelayPointStatus.REVOKED);
        rp.setIsActive(false);
        rp.setUpdatedAt(Instant.now());
        UUID tenant = tenantId != null ? tenantId : TenantContextHolder.SYSTEM_TENANT;
        return relayPoints.save(rp)
                .flatMap(saved -> deactivateOperator(tenant, saved.getCoreRelayPointId())
                        .then(notifyOperator(tenant, saved, reason, loginUrl))
                        .thenReturn(saved));
    }

    private Mono<Void> deactivateOperator(UUID tenant, UUID hubId) {
        if (hubId == null) return Mono.empty();
        return relayOperators.findByHubId(tenant, hubId)
                .flatMap(profile -> relayOperators.save(profile.deactivate()))
                .doOnNext(p -> log.info("Relay operator actor={} hub={} deactivated (revocation)", p.actorId(), hubId))
                .onErrorResume(e -> {
                    log.error("Relay hub {} revoked, operator profile not deactivated", hubId, e);
                    return Mono.empty();
                })
                .then();
    }

    /**
     * The operator's actor (H1): their relay operator profile, else the owner freelancer's core user. Empty when
     * neither is known. Never a profile id.
     */
    public Mono<UUID> operatorActor(UUID tenant, GofpRelayPoint rp) {
        Mono<UUID> fromProfile = rp.getCoreRelayPointId() == null ? Mono.empty()
                : relayOperators.findByHubId(tenant, rp.getCoreRelayPointId()).map(p -> p.actorId());
        Mono<UUID> fromFreelancer = rp.getCoreFreelancerId() == null ? Mono.empty()
                : freelancers.findById(rp.getCoreFreelancerId()).mapNotNull(f -> f.getCoreUserId());
        return fromProfile.onErrorResume(e -> Mono.empty()).switchIfEmpty(fromFreelancer);
    }

    private Mono<Void> notifyOperator(UUID tenant, GofpRelayPoint rp, String reason, String loginUrl) {
        Mono<Void> inApp = operatorActor(tenant, rp)
                .flatMap(actor -> push.sendLocalized(tenant, actor, GofpNotificationTemplates.RELAY_STATUS_REVOKED_REASON,
                        Map.of("reason", reason != null ? reason : "")))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Relay point {} revoked: no operator actor found, in-app notification not sent", rp.getId())))
                .onErrorResume(e -> {
                    log.error("Relay point {} revoked: in-app notification failed", rp.getId(), e);
                    return Mono.empty();
                })
                .then();
        Mono<Void> mail = Mono.fromRunnable(() -> {
                    if (rp.getOwnerEmail() != null && !rp.getOwnerEmail().isBlank()) {
                        email.sendAccountRevoked(rp.getOwnerEmail(), loginUrl);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Relay point {} revoked: e-mail failed", rp.getId(), e);
                    return Mono.empty();
                })
                .then();
        return Mono.when(inApp, mail);
    }
}
