package com.yowyob.tiibntick.core.organization.application.service;

import com.yowyob.tiibntick.core.organization.application.port.in.ManageFreelancerOrgUseCase;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPayload;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgDidAnchorPort;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgEventPublisherPort;
import com.yowyob.tiibntick.core.organization.application.port.out.FreelancerOrgRepositoryPort;
import com.yowyob.tiibntick.core.organization.domain.enums.KycLevel;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgCreatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgSuspendedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.FreelancerOrgVerifiedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.KycLevelUpgradedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.SubDelivererAssociatedEvent;
import com.yowyob.tiibntick.core.organization.domain.event.SubDelivererRevokedEvent;
import com.yowyob.tiibntick.core.organization.domain.model.FreelancerOrganization;
import com.yowyob.tiibntick.core.organization.domain.vo.AssociatedDelivererRef;
import com.yowyob.tiibntick.core.organization.domain.vo.FreelancerCapabilities;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Application service implementing {@link ManageFreelancerOrgUseCase}.
 *
 * <p>Orchestrates the FreelancerOrganization aggregate lifecycle:
 * <ol>
 *   <li>Delegates persistence to {@link FreelancerOrgRepositoryPort}.</li>
 *   <li>Publishes domain events via {@link FreelancerOrgEventPublisherPort}.</li>
 * </ol>
 *
 * <p>This class is deliberately <strong>not</strong> annotated with {@code @Service}
 * to remain framework-agnostic. Spring wiring is done in
 * {@link com.yowyob.tiibntick.core.organization.config.OrganizationCoreAutoConfiguration}.
 *
 * <h3>Security</h3>
 * <p>Write operations require {@code freelancer_org:write} or {@code freelancer_org:admin};
 * read operations require {@code freelancer_org:read}. Enforcement is declarative via
 * {@code @RequirePermission} from {@code tnt-roles-core}'s AOP aspect.
 *
 * @author MANFOUO Braun
 */
public class FreelancerOrgService implements ManageFreelancerOrgUseCase {

    private static final Logger log = LoggerFactory.getLogger(FreelancerOrgService.class);

    private final FreelancerOrgRepositoryPort repository;
    private final FreelancerOrgEventPublisherPort eventPublisher;
    private final FreelancerOrgDidAnchorPort didAnchorPort;

    /**
     * Constructor injection — no field injection to keep the class testable
     * without a Spring context.
     *
     * @param repository     persistence port for FreelancerOrganization aggregates
     * @param eventPublisher outbound event publishing port
     * @param didAnchorPort  outbound port for anchoring the org's blockchain DID
     *                       (implemented by {@code tnt-trust-core})
     */
    public FreelancerOrgService(FreelancerOrgRepositoryPort repository,
                                 FreelancerOrgEventPublisherPort eventPublisher,
                                 FreelancerOrgDidAnchorPort didAnchorPort) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.didAnchorPort = didAnchorPort;
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * Requires permission: {@code freelancer_org:write}.
     */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<FreelancerOrganization> registerFreelancerOrg(UUID organizationId,
                                                               UUID ownerActorId,
                                                               String tradeName) {
        return repository.existsByTradeName(tradeName)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "A FreelancerOrganization with trade name '" + tradeName + "' already exists"));
                    }
                    FreelancerOrganization org = FreelancerOrganization.register(
                            organizationId, ownerActorId, tradeName);
                    return repository.save(org)
                            .flatMap(saved -> eventPublisher
                                    .publishFreelancerOrgCreated(FreelancerOrgCreatedEvent.of(
                                            saved.getId().value(), saved.getTenantId(),
                                            saved.getTradeName(), saved.getOwnerActorId()))
                                    .thenReturn(saved));
                });
    }

    // ─── KYC lifecycle ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * Requires permission: {@code freelancer_org:write}.
     */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<FreelancerOrganization> upgradeKycToBasic(OrganizationId orgId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    KycLevel previous = org.getKycLevel();
                    org.upgradeKycToBasic();
                    return repository.save(org)
                            .flatMap(saved -> eventPublisher
                                    .publishKycLevelUpgraded(KycLevelUpgradedEvent.of(
                                            saved.getId().value(), saved.getTenantId(),
                                            saved.getOwnerActorId(), previous, KycLevel.BASIC))
                                    .thenReturn(saved));
                });
    }

    /**
     * {@inheritDoc}
     * Requires permission: {@code freelancer_org:write}.
     */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<FreelancerOrganization> upgradeKycToFull(OrganizationId orgId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    KycLevel previous = org.getKycLevel();
                    org.upgradeKycToFull();
                    return repository.save(org)
                            .flatMap(saved -> eventPublisher
                                    .publishKycLevelUpgraded(KycLevelUpgradedEvent.of(
                                            saved.getId().value(), saved.getTenantId(),
                                            saved.getOwnerActorId(), previous, KycLevel.FULL))
                                    .thenReturn(saved));
                });
    }

    // ─── Admin lifecycle ──────────────────────────────────────────────────────

    /** {@inheritDoc} Requires permission: {@code freelancer_org:write}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<FreelancerOrganization> submitForReview(OrganizationId orgId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.submitForReview();
                    return repository.save(org);
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:admin}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "admin")
    public Mono<FreelancerOrganization> verifyFreelancerOrg(OrganizationId orgId,
                                                             UUID adminActorId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.verify();
                    return repository.save(org)
                            .flatMap(saved -> eventPublisher
                                    .publishFreelancerOrgVerified(FreelancerOrgVerifiedEvent.of(
                                            saved.getId().value(), saved.getTenantId(),
                                            saved.getOwnerActorId(), saved.getKycLevel(),
                                            adminActorId))
                                    .thenReturn(saved));
                })
                .flatMap(this::anchorDidIfAbsent);
    }

    /**
     * Issues the organization's blockchain DID if it does not already have one.
     * Best-effort: a failure here must never fail the verification flow.
     */
    private Mono<FreelancerOrganization> anchorDidIfAbsent(FreelancerOrganization org) {
        if (org.getBlockchainDid() != null) {
            return Mono.just(org);
        }
        final var payload = new FreelancerOrgDidAnchorPayload(
                org.getId().value(), org.getTenantId(), org.getTradeName());
        return didAnchorPort.issueDid(payload)
                .flatMap(did -> {
                    org.assignBlockchainDid(did);
                    return repository.save(org);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to anchor blockchain DID for FreelancerOrg {}: {}",
                            org.getId(), e.getMessage());
                    return Mono.just(org);
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:write}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<FreelancerOrganization> activateFreelancerOrg(OrganizationId orgId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.activate();
                    return repository.save(org);
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:admin}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "admin")
    public Mono<FreelancerOrganization> suspendFreelancerOrg(OrganizationId orgId,
                                                              String reason,
                                                              UUID adminActorId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.suspend();
                    return repository.save(org)
                            .flatMap(saved -> eventPublisher
                                    .publishFreelancerOrgSuspended(FreelancerOrgSuspendedEvent.of(
                                            saved.getId().value(), saved.getTenantId(),
                                            saved.getOwnerActorId(), reason, adminActorId))
                                    .thenReturn(saved));
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:admin}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "admin")
    public Mono<FreelancerOrganization> unsuspendFreelancerOrg(OrganizationId orgId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.unsuspend();
                    return repository.save(org);
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:admin}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "admin")
    public Mono<FreelancerOrganization> blacklistFreelancerOrg(OrganizationId orgId,
                                                                UUID adminActorId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.blacklist();
                    return repository.save(org);
                });
    }

    // ─── Profile management ───────────────────────────────────────────────────

    /** {@inheritDoc} Requires permission: {@code freelancer_org:write}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<FreelancerOrganization> updateCapabilities(OrganizationId orgId,
                                                            FreelancerCapabilities capabilities) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.updateCapabilities(capabilities);
                    return repository.save(org);
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:write}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<FreelancerOrganization> updateOperationalZones(OrganizationId orgId,
                                                                List<ServiceZone> zones) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.setOperationalZones(zones);
                    return repository.save(org);
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:write}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<FreelancerOrganization> updateTradeName(OrganizationId orgId, String tradeName) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.updateTradeName(tradeName);
                    return repository.save(org);
                });
    }

    // ─── Sub-deliverer management ─────────────────────────────────────────────

    /** {@inheritDoc} Requires permission: {@code freelancer_org:write}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<AssociatedDelivererRef> inviteSubDeliverer(OrganizationId orgId,
                                                            UUID delivererActorId,
                                                            BigDecimal commissionRate) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    AssociatedDelivererRef ref = org.inviteSubDeliverer(delivererActorId, commissionRate);
                    return repository.save(org).thenReturn(ref);
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:write}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<AssociatedDelivererRef> acceptSubDelivererInvitation(OrganizationId orgId,
                                                                      UUID delivererActorId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.acceptSubDeliverer(delivererActorId);
                    return repository.save(org)
                            .flatMap(saved -> {
                                AssociatedDelivererRef ref = saved.getSubDeliverers().stream()
                                        .filter(r -> r.delivererActorId().equals(delivererActorId))
                                        .findFirst()
                                        .orElseThrow();
                                return eventPublisher
                                        .publishSubDelivererAssociated(SubDelivererAssociatedEvent.of(
                                                saved.getId().value(), saved.getTenantId(),
                                                saved.getOwnerActorId(), delivererActorId,
                                                ref.commissionRate()))
                                        .thenReturn(ref);
                            });
                });
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:write}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "write")
    public Mono<AssociatedDelivererRef> revokeSubDeliverer(OrganizationId orgId,
                                                            UUID delivererActorId) {
        return repository.findById(orgId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FreelancerOrganization not found: " + orgId)))
                .flatMap(org -> {
                    org.revokeSubDeliverer(delivererActorId);
                    return repository.save(org)
                            .flatMap(saved -> {
                                AssociatedDelivererRef ref = saved.getSubDeliverers().stream()
                                        .filter(r -> r.delivererActorId().equals(delivererActorId))
                                        .findFirst()
                                        .orElseThrow();
                                return eventPublisher
                                        .publishSubDelivererRevoked(SubDelivererRevokedEvent.of(
                                                saved.getId().value(), saved.getTenantId(),
                                                saved.getOwnerActorId(), delivererActorId))
                                        .thenReturn(ref);
                            });
                });
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /** {@inheritDoc} Requires permission: {@code freelancer_org:read}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "read")
    public Mono<FreelancerOrganization> findById(OrganizationId id) {
        return repository.findById(id);
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:read}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "read")
    public Mono<FreelancerOrganization> findByOwnerActorId(UUID ownerActorId) {
        return repository.findByOwnerActorId(ownerActorId);
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:read}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "read")
    public Flux<FreelancerOrganization> findAvailableInZone(double latitude,
                                                             double longitude,
                                                             double radiusKm) {
        return repository.findByZoneProximity(latitude, longitude, radiusKm);
    }

    /** {@inheritDoc} Requires permission: {@code freelancer_org:read}. */
    @Override
    @RequirePermission(resource = "freelancer_org", action = "read")
    public Flux<AssociatedDelivererRef> listSubDeliverers(OrganizationId orgId) {
        return repository.findSubDeliverersByOrgId(orgId);
    }
}
