package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.command.ApproveKycCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RejectKycCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.SubmitKycCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.KycRecordRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntClientProfileRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.event.TntTpDomainEvents.KycStatusChangedEvent;
import com.yowyob.tiibntick.core.tp.domain.exception.KycAlreadyVerifiedException;
import com.yowyob.tiibntick.core.tp.domain.exception.TntThirdPartyNotFoundException;
import com.yowyob.tiibntick.core.tp.domain.model.KycRecord;
import com.yowyob.tiibntick.core.tp.domain.model.enums.KycStatus;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for KYC (Know Your Customer) document management.
 *
 * @author MANFOUO Braun
 */
@Service
public class TpKycService {

    private final KycRecordRepository kycRecordRepository;
    private final TntClientProfileRepository profileRepository;
    private final TntTpEventPublisher eventPublisher;

    public TpKycService(
            KycRecordRepository kycRecordRepository,
            TntClientProfileRepository profileRepository,
            TntTpEventPublisher eventPublisher) {
        this.kycRecordRepository = kycRecordRepository;
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Submits KYC documents for review.
     * Rejects submission if a pending review already exists or profile is already approved.
     */
    @RequirePermission(resource = "actor", action = "write")
    @Transactional
    public Mono<KycRecord> submit(SubmitKycCommand command) {
        return profileRepository.findByThirdPartyId(command.tenantId(), command.thirdPartyId())
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(command.thirdPartyId())))
                .flatMap(profile -> {
                    if (profile.isKycVerified()) {
                        return Mono.error(new KycAlreadyVerifiedException(command.thirdPartyId()));
                    }
                    return kycRecordRepository.existsPendingByThirdPartyId(
                            command.tenantId(), command.thirdPartyId())
                            .flatMap(hasPending -> {
                                if (Boolean.TRUE.equals(hasPending)) {
                                    return Mono.error(new IllegalStateException(
                                            "A pending KYC review already exists for thirdPartyId="
                                                    + command.thirdPartyId()));
                                }
                                KycRecord record = KycRecord.submit(
                                        command.tenantId(), command.thirdPartyId(),
                                        command.documentType(), command.documentStorageKey(),
                                        command.selfieStorageKey(), command.documentNumber(),
                                        command.documentExpiryDate());
                                return kycRecordRepository.save(record)
                                        .flatMap(saved -> {
                                            var event = new KycStatusChangedEvent(
                                                    saved.getId(), saved.getTenantId(),
                                                    saved.getThirdPartyId(),
                                                    KycStatus.NOT_SUBMITTED, KycStatus.PENDING_REVIEW,
                                                    null, Instant.now());
                                            return eventPublisher.publish(event).thenReturn(saved);
                                        });
                            });
                });
    }

    /**
     * Approves a KYC record and updates the client profile status.
     */
    @RequirePermission(resource = "actor", action = "approve")
    @Transactional
    public Mono<KycRecord> approve(ApproveKycCommand command) {
        return kycRecordRepository.findById(command.kycRecordId())
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(
                        "KycRecord not found: " + command.kycRecordId())))
                .flatMap(record -> {
                    if (!record.isPendingReview()) {
                        return Mono.error(new IllegalStateException(
                                "KycRecord " + command.kycRecordId() + " is not in PENDING_REVIEW state"));
                    }
                    KycRecord approved = record.approve(command.reviewerAdminId());
                    return kycRecordRepository.save(approved)
                            .flatMap(saved -> profileRepository
                                    .findByThirdPartyId(saved.getTenantId(), saved.getThirdPartyId())
                                    .flatMap(profile -> profileRepository.save(
                                            profile.updateKycStatus(KycStatus.APPROVED)))
                                    .thenReturn(saved))
                            .flatMap(saved -> {
                                var event = new KycStatusChangedEvent(
                                        saved.getId(), saved.getTenantId(),
                                        saved.getThirdPartyId(),
                                        KycStatus.PENDING_REVIEW, KycStatus.APPROVED,
                                        null, Instant.now());
                                return eventPublisher.publish(event).thenReturn(saved);
                            });
                });
    }

    /**
     * Rejects a KYC record with an explanatory reason.
     */
    @RequirePermission(resource = "actor", action = "approve")
    @Transactional
    public Mono<KycRecord> reject(RejectKycCommand command) {
        return kycRecordRepository.findById(command.kycRecordId())
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(
                        "KycRecord not found: " + command.kycRecordId())))
                .flatMap(record -> {
                    if (!record.isPendingReview()) {
                        return Mono.error(new IllegalStateException(
                                "KycRecord " + command.kycRecordId() + " is not in PENDING_REVIEW state"));
                    }
                    KycRecord rejected = record.reject(command.reviewerAdminId(), command.rejectionReason());
                    return kycRecordRepository.save(rejected)
                            .flatMap(saved -> profileRepository
                                    .findByThirdPartyId(saved.getTenantId(), saved.getThirdPartyId())
                                    .flatMap(profile -> profileRepository.save(
                                            profile.updateKycStatus(KycStatus.REJECTED)))
                                    .thenReturn(saved))
                            .flatMap(saved -> {
                                var event = new KycStatusChangedEvent(
                                        saved.getId(), saved.getTenantId(),
                                        saved.getThirdPartyId(),
                                        KycStatus.PENDING_REVIEW, KycStatus.REJECTED,
                                        command.rejectionReason(), Instant.now());
                                return eventPublisher.publish(event).thenReturn(saved);
                            });
                });
    }

    @RequirePermission(resource = "actor", action = "read")
    public Mono<KycRecord> getLatestByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return kycRecordRepository.findLatestByThirdPartyId(tenantId, thirdPartyId);
    }

    @RequirePermission(resource = "actor", action = "read")
    public Flux<KycRecord> getHistoryByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return kycRecordRepository.findAllByThirdPartyId(tenantId, thirdPartyId);
    }
}
