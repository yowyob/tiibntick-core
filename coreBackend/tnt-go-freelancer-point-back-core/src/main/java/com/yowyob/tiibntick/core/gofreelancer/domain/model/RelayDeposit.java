package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Temporary GOFP product mirror of a hub deposit.
 *
 * <p>Authoritative stock lives in {@code tnt-inventory-core} ({@code HubPackageEntry});
 * delivery step in {@code tnt-delivery-core}; hub identity/occupancy in {@code tnt-geo-core}.
 * Kept for UX/billing until cut-over — synchronised by {@code RelayDepositService}.</p>
 *
 * @author MANFOUO BRAUN
 * @deprecated Prefer HubPackageEntry + delivery lifecycle; retain as sync mirror only.
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("relay_deposits")
public class RelayDeposit implements Persistable<UUID>, TntPersistableEntity {
    @Transient @Builder.Default private boolean isNew = true;

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }


    @Id
    @Column("id")
    private UUID id;

    @NotNull
    @Column("packet_id")
    private UUID packetId;

    @NotNull
    @Column("client_id")
    private UUID clientId;

    @NotNull
    @Column("logistics_id")
    private UUID relayPointId;

    @NotNull
    @Column("storage_fee")
    private Double storageFee;

    @Column("penalty_fee")
    private Double penaltyFee;

    @Column("currency")
    private String currency;

    @NotNull
    @Column("status")
    private RelayDepositStatus status;

    @Column("created_at")
    private Instant createdAt;

    @Column("retrieved_at")
    private Instant retrievedAt;
}
