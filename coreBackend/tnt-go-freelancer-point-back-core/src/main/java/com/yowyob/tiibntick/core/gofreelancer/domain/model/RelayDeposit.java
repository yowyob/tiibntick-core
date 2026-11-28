package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A parcel deposited at a relay point, as the Go-Freelancer API shows it.
 *
 * <p>Read-only view of a {@code tnt-inventory-core} {@code HubPackageEntry} (same id): the deposit
 * is no longer stored by Go-Freelancer. {@code relayPointId} is the hub id, {@code clientId} the
 * owner of the entry (the person who collects the parcel), {@code status} is {@code RETRIEVED} once
 * the entry is picked up, {@code DELIVERED} before.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayDeposit {

    private UUID id;
    private UUID packetId;
    private UUID clientId;
    private UUID relayPointId;
    private Double storageFee;
    private Double penaltyFee;
    private String currency;
    private RelayDepositStatus status;
    private Instant createdAt;
    private Instant retrievedAt;

    /**
     * Kept so the JSON of a deposit keeps its historical {@code "new"} key (a persistence flag of
     * the former table mapping). A view is never "new": always {@code false}.
     */
    public boolean isNew() {
        return false;
    }
}
