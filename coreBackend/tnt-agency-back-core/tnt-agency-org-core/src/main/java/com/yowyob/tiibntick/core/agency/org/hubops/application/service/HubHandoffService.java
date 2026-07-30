package com.yowyob.tiibntick.core.agency.org.hubops.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto.HubHandoffResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubHandoffRequestR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.entity.HubHandoffRequestEntity;
import com.yowyob.tiibntick.core.agency.org.hubops.application.mapper.HubHandoffMapper;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.vo.HandoffStatus;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.vo.HandoffType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Deposit / withdraw handoffs at agency hubs: livreur/client request → gérant validates
 * (or hub-initiated withdraw awaiting client confirm).
 */
@Service
@RequiredArgsConstructor
public class HubHandoffService {

    private final HubHandoffRequestR2dbcRepository handoffRepo;
    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final HubParcelService hubParcelService;

    public Flux<HubHandoffResponse> listPending(UUID tenantId, UUID hubId) {
        return handoffRepo.findByHubIdAndTenantIdAndStatus(hubId, tenantId, HandoffStatus.PENDING.name())
                .concatWith(handoffRepo.findByHubIdAndTenantIdAndStatus(
                        hubId, tenantId, HandoffStatus.AWAITING_CLIENT_CONFIRM.name()))
                .map(HubHandoffMapper::toResponse);
    }

    public Flux<HubHandoffResponse> listByHub(UUID tenantId, UUID hubId) {
        return handoffRepo.findByHubIdAndTenantIdOrderByCreatedAtDesc(hubId, tenantId)
                .map(HubHandoffMapper::toResponse);
    }

    @Transactional
    public Mono<HubHandoffResponse> createRequest(CreateInput input) {
        if (input.trackingCode() == null || input.trackingCode().isBlank()) {
            return Mono.error(new TntValidationException("trackingCode is required"));
        }
        HandoffType type = parseType(input.handoffType());
        Instant now = Instant.now();
        return hubRepo.findByIdAndTenantId(input.hubId(), input.tenantId())
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Hub introuvable: " + input.hubId())))
                .flatMap(hub -> {
                    HubHandoffRequestEntity e = new HubHandoffRequestEntity();
                    e.setId(UUID.randomUUID());
                    e.setTenantId(input.tenantId());
                    e.setAgencyId(hub.getAgencyId());
                    e.setHubId(input.hubId());
                    e.setHandoffType(type.name());
                    e.setStatus(HandoffStatus.PENDING.name());
                    e.setMissionId(input.missionId());
                    e.setPackageId(input.packageId());
                    e.setTrackingCode(input.trackingCode().trim().toUpperCase(Locale.ROOT));
                    e.setRequesterActorId(input.requesterActorId());
                    e.setRequesterRole(input.requesterRole());
                    e.setRequesterLabel(input.requesterLabel());
                    e.setWithdrawParty(input.withdrawParty());
                    e.setNotes(input.notes());
                    e.setCreatedAt(now);
                    e.setUpdatedAt(now);
                    e.setVersion(0L);
                    return handoffRepo.save(e);
                })
                .map(HubHandoffMapper::toResponse);
    }

    /**
     * Gérant validates a pending deposit/withdraw from livreur or client scan.
     */
    @Transactional
    public Mono<HubHandoffResponse> approve(ApproveInput input) {
        Instant now = Instant.now();
        return requireHandoff(input.handoffId(), input.tenantId())
                .flatMap(e -> {
                    if (!HandoffStatus.PENDING.name().equals(e.getStatus())) {
                        return Mono.error(new TntValidationException(
                                "Seule une demande PENDING peut être approuvée."));
                    }
                    e.setValidatedByActorId(input.validatorActorId());
                    e.setValidatedByLabel(input.validatorLabel());
                    e.setValidatedAt(now);
                    e.setUpdatedAt(now);

                    if (HandoffType.DEPOSIT.name().equals(e.getHandoffType())) {
                        return hubParcelService.deposit(new HubParcelService.DepositInput(
                                        e.getTenantId(), e.getHubId(), e.getMissionId(),
                                        e.getTrackingCode(), e.getPackageId(),
                                        e.getRequesterActorId(), e.getRequesterLabel()))
                                .then(Mono.defer(() -> {
                                    e.setStatus(HandoffStatus.COMPLETED.name());
                                    e.setCompletedAt(now);
                                    return handoffRepo.save(e);
                                }));
                    }

                    // WITHDRAW from scan — complete immediately after hub validation
                    return hubParcelService.withdraw(new HubParcelService.WithdrawInput(
                                    e.getTenantId(), e.getTrackingCode(),
                                    e.getRequesterLabel() != null ? e.getRequesterLabel() : "SCAN",
                                    true, e.getRequesterActorId()))
                            .then(Mono.defer(() -> {
                                e.setStatus(HandoffStatus.COMPLETED.name());
                                e.setCompletedAt(now);
                                return handoffRepo.save(e);
                            }));
                })
                .map(HubHandoffMapper::toResponse);
    }

    @Transactional
    public Mono<HubHandoffResponse> reject(UUID tenantId, UUID handoffId,
                                           UUID validatorActorId, String validatorLabel, String notes) {
        Instant now = Instant.now();
        return requireHandoff(handoffId, tenantId)
                .flatMap(e -> {
                    if (!HandoffStatus.PENDING.name().equals(e.getStatus())
                            && !HandoffStatus.AWAITING_CLIENT_CONFIRM.name().equals(e.getStatus())) {
                        return Mono.error(new TntValidationException(
                                "Cette demande ne peut plus être rejetée."));
                    }
                    e.setStatus(HandoffStatus.REJECTED.name());
                    e.setValidatedByActorId(validatorActorId);
                    e.setValidatedByLabel(validatorLabel);
                    e.setValidatedAt(now);
                    e.setUpdatedAt(now);
                    if (notes != null) e.setNotes(notes);
                    return handoffRepo.save(e);
                })
                .map(HubHandoffMapper::toResponse);
    }

    /**
     * Hub claims client retrieved the parcel — awaits client confirmation before stock out.
     */
    @Transactional
    public Mono<HubHandoffResponse> claimClientWithdraw(ClaimClientWithdrawInput input) {
        Instant now = Instant.now();
        return hubRepo.findByIdAndTenantId(input.hubId(), input.tenantId())
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Hub introuvable: " + input.hubId())))
                .flatMap(hub -> {
                    HubHandoffRequestEntity e = new HubHandoffRequestEntity();
                    e.setId(UUID.randomUUID());
                    e.setTenantId(input.tenantId());
                    e.setAgencyId(hub.getAgencyId());
                    e.setHubId(input.hubId());
                    e.setHandoffType(HandoffType.WITHDRAW.name());
                    e.setStatus(HandoffStatus.AWAITING_CLIENT_CONFIRM.name());
                    e.setTrackingCode(input.trackingCode().trim().toUpperCase(Locale.ROOT));
                    e.setMissionId(input.missionId());
                    e.setPackageId(input.packageId());
                    e.setWithdrawParty("CLIENT");
                    e.setRequesterRole("OPERATOR");
                    e.setRequesterActorId(input.operatorActorId());
                    e.setRequesterLabel(input.operatorLabel());
                    e.setValidatedByActorId(input.operatorActorId());
                    e.setValidatedByLabel(input.operatorLabel());
                    e.setValidatedAt(now);
                    e.setNotes(input.notes());
                    e.setCreatedAt(now);
                    e.setUpdatedAt(now);
                    e.setVersion(0L);
                    return handoffRepo.save(e);
                })
                .map(HubHandoffMapper::toResponse);
    }

    /**
     * Client confirms they received the parcel after hub-initiated claim.
     */
    @Transactional
    public Mono<HubHandoffResponse> confirmClientWithdraw(UUID tenantId, UUID handoffId,
                                                          UUID clientActorId, String clientLabel) {
        Instant now = Instant.now();
        return requireHandoff(handoffId, tenantId)
                .flatMap(e -> {
                    if (!HandoffStatus.AWAITING_CLIENT_CONFIRM.name().equals(e.getStatus())) {
                        return Mono.error(new TntValidationException(
                                "Cette demande n'attend pas de confirmation client."));
                    }
                    String withdrawnBy = clientLabel != null ? clientLabel : "CLIENT";
                    return hubParcelService.withdraw(new HubParcelService.WithdrawInput(
                                    e.getTenantId(), e.getTrackingCode(), withdrawnBy, true, clientActorId))
                            .then(Mono.defer(() -> {
                                e.setStatus(HandoffStatus.COMPLETED.name());
                                e.setRequesterActorId(clientActorId != null
                                        ? clientActorId : e.getRequesterActorId());
                                e.setRequesterLabel(withdrawnBy);
                                e.setCompletedAt(now);
                                e.setUpdatedAt(now);
                                return handoffRepo.save(e);
                            }));
                })
                .map(HubHandoffMapper::toResponse);
    }

    /**
     * Manual deposit recorded by hub operator (no prior pending request).
     */
    @Transactional
    public Mono<HubHandoffResponse> manualDeposit(ManualDepositInput input) {
        return createRequest(new CreateInput(
                        input.tenantId(), input.hubId(), HandoffType.DEPOSIT.name(),
                        input.missionId(), input.packageId(), input.trackingCode(),
                        input.delivererActorId(), "DELIVERER", input.delivererLabel(),
                        null, input.notes()))
                .flatMap(created -> approve(new ApproveInput(
                        input.tenantId(), created.id(),
                        input.operatorActorId(), input.operatorLabel())));
    }

    private Mono<HubHandoffRequestEntity> requireHandoff(UUID id, UUID tenantId) {
        return handoffRepo.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HANDOFF_NOT_FOUND", "Demande hub introuvable: " + id)));
    }

    private static HandoffType parseType(String raw) {
        try {
            return HandoffType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new TntValidationException("handoffType invalide: " + raw);
        }
    }

    public record CreateInput(
            UUID tenantId, UUID hubId, String handoffType,
            UUID missionId, UUID packageId, String trackingCode,
            UUID requesterActorId, String requesterRole, String requesterLabel,
            String withdrawParty, String notes) {}

    public record ApproveInput(
            UUID tenantId, UUID handoffId, UUID validatorActorId, String validatorLabel) {}

    public record ClaimClientWithdrawInput(
            UUID tenantId, UUID hubId, String trackingCode,
            UUID missionId, UUID packageId,
            UUID operatorActorId, String operatorLabel, String notes) {}

    public record ManualDepositInput(
            UUID tenantId, UUID hubId, String trackingCode,
            UUID missionId, UUID packageId,
            UUID delivererActorId, String delivererLabel,
            UUID operatorActorId, String operatorLabel, String notes) {}
}
