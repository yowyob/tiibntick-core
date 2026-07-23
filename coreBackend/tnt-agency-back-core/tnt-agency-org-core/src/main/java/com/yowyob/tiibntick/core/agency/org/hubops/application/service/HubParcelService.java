package com.yowyob.tiibntick.core.agency.org.hubops.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.org.adapter.out.clients.TrustPort;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto.HubParcelResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.clients.InventoryCorePort;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubParcelRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.application.mapper.HubParcelMapper;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.HubParcelRecord;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.support.TrackingCodeGenerator;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.HubParcelWithdrawn;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.ParcelAtAgencyHub;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubParcelService {

    private final HubParcelRecordR2dbcRepository parcelRepo;
    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final InventoryCorePort inventoryCore;
    private final HubOccupancyService occupancyService;
    private final AgencyEventPublisher eventPublisher;
    private final TrustPort trust;

    public record DepositInput(
            UUID tenantId, UUID hubId, UUID missionId, String trackingCode, UUID packageId) {}

    public record WithdrawInput(
            UUID tenantId, String trackingCode, String withdrawnBy, boolean identityVerified,
            UUID pickedUpByActorId) {}

    @Transactional
    public Mono<HubParcelResponse> deposit(DepositInput input) {
        Instant now = Instant.now();
        UUID packageId = input.packageId() != null ? input.packageId() : UUID.randomUUID();
        String trackingCode = (input.trackingCode() == null || input.trackingCode().isBlank())
                ? TrackingCodeGenerator.generate()
                : input.trackingCode().trim().toUpperCase();

        return hubRepo.findByIdAndTenantId(input.hubId(), input.tenantId())
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Hub introuvable: " + input.hubId())))
                .flatMap(hub -> {
                    if (hub.getCoreHubId() == null) {
                        return Mono.error(new TntValidationException(
                                "Hub non synchronisé avec inventory-core (coreHubId manquant)."));
                    }
                    HubParcelRecord record = HubParcelRecord.deposit(
                            UUID.randomUUID(), input.tenantId(), input.hubId(),
                            packageId, input.missionId(), trackingCode,
                            hub.getRetentionDelayHours() != null ? hub.getRetentionDelayHours() : 72,
                            now);
                    return inventoryCore.depositPackage(new InventoryCorePort.DepositPackageRequest(
                                    input.tenantId(), hub.getCoreHubId(), packageId,
                                    trackingCode, null, null, null))
                            .doOnNext(view -> record.linkCoreEntry(view.id(), now))
                            .then(parcelRepo.save(HubParcelMapper.toEntity(record)))
                            .map(HubParcelMapper::toDomain)
                            .flatMap(saved -> occupancyService.adjustOccupancy(
                                            input.tenantId(), input.hubId(), 1)
                                    .thenReturn(saved))
                            .flatMap(saved -> eventPublisher.publish(new ParcelAtAgencyHub(
                                            UUID.randomUUID(), saved.getId(), saved.getTenantId(),
                                            saved.getHubId(), saved.getPackageId(), saved.getTrackingCode(),
                                            saved.getWithdrawalDeadline(), now))
                                    .thenReturn(saved))
                            .flatMap(saved -> trust.recordHubDeposit(new TrustPort.HubDepositTransaction(
                                            saved.getHubId(),
                                            saved.getPackageId(),
                                            saved.getMissionId() != null ? saved.getMissionId() : saved.getId(),
                                            saved.getTrackingCode(),
                                            now))
                                    .thenReturn(saved))
                            .map(HubParcelResponse::from);
                });
    }

    @Transactional
    public Mono<HubParcelResponse> withdraw(WithdrawInput input) {
        Instant now = Instant.now();
        return parcelRepo.findByTrackingCodeAndTenantId(input.trackingCode(), input.tenantId())
                .map(HubParcelMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "PARCEL_NOT_FOUND", "Colis introuvable: " + input.trackingCode())))
                .flatMap(record -> inventoryCore.pickupPackage(
                                new InventoryCorePort.PickupPackageRequest(
                                        input.trackingCode(), input.pickedUpByActorId()))
                        .then(Mono.defer(() -> {
                            record.withdraw(input.withdrawnBy(), input.identityVerified(), now);
                            return parcelRepo.save(HubParcelMapper.toEntity(record));
                        }))
                        .map(HubParcelMapper::toDomain)
                        .flatMap(saved -> occupancyService.adjustOccupancy(
                                        input.tenantId(), saved.getHubId(), -1)
                                .thenReturn(saved))
                        .flatMap(saved -> eventPublisher.publish(new HubParcelWithdrawn(
                                        UUID.randomUUID(), saved.getId(), saved.getTenantId(),
                                        saved.getHubId(), saved.getPackageId(), saved.getTrackingCode(),
                                        input.withdrawnBy(), input.identityVerified(), now))
                                .thenReturn(saved))
                        .flatMap(saved -> trust.recordHubWithdrawal(new TrustPort.HubWithdrawalTransaction(
                                        saved.getHubId(),
                                        saved.getPackageId(),
                                        saved.getTrackingCode(),
                                        input.withdrawnBy(),
                                        input.identityVerified(),
                                        now))
                                .thenReturn(saved))
                        .map(HubParcelResponse::from));
    }

    public Flux<HubParcelResponse> listByHub(UUID tenantId, UUID hubId) {
        return parcelRepo.findByHubIdAndTenantId(hubId, tenantId)
                .map(HubParcelMapper::toDomain)
                .map(HubParcelResponse::from);
    }
}
