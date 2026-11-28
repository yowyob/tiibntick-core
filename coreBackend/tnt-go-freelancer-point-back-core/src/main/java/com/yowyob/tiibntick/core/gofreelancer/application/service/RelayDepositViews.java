package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import com.yowyob.tiibntick.core.inventory.domain.model.HubPackageEntry;

import java.math.BigDecimal;

/** Shows an inventory hub package entry as a Go-Freelancer {@link RelayDeposit} (same id). */
public final class RelayDepositViews {

    private RelayDepositViews() {
    }

    public static RelayDeposit of(HubPackageEntry entry) {
        return RelayDeposit.builder()
                .id(entry.id())
                .packetId(entry.packageId())
                .clientId(entry.ownerActorId())
                .relayPointId(entry.hubId())
                .storageFee(toDouble(entry.storageFee()))
                .penaltyFee(toDouble(entry.penaltyFee()))
                .currency(entry.feeCurrency())
                .status(entry.isPickedUp() ? RelayDepositStatus.RETRIEVED : RelayDepositStatus.DELIVERED)
                .createdAt(entry.depositedAt())
                .retrievedAt(entry.pickedUpAt())
                .build();
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
