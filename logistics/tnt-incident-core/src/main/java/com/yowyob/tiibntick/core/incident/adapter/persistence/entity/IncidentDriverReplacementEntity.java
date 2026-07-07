package com.yowyob.tiibntick.core.incident.adapter.persistence.entity;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
/**
 * R2DBC entity mapped to the tnt_incident_driver_replacements table.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Table("tnt_incident_driver_replacements")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class IncidentDriverReplacementEntity implements Persistable<UUID> {
    @Id private UUID id;

    @Transient
    private boolean isNew;
    @Column("incident_id") private UUID incidentId;
    @Column("original_driver_id") private UUID originalDriverId;
    @Column("original_vehicle_id") private UUID originalVehicleId;
    @Column("replacement_driver_id") private UUID replacementDriverId;
    @Column("replacement_vehicle_id") private UUID replacementVehicleId;
    @Column("replacement_agency_id") private UUID replacementAgencyId;
    @Column("handover_lat") private double handoverLatitude;
    @Column("handover_lng") private double handoverLongitude;
    @Column("handover_address") private String handoverAddress;
    @Column("handover_scheduled_at") private Instant handoverScheduledAt;
    @Column("handover_at") private Instant handoverAt;
    @Column("handover_status") private String handoverStatus;
    @Column("original_driver_confirmed_at") private Instant originalDriverConfirmedAt;
    @Column("replacement_driver_confirmed_at") private Instant replacementDriverConfirmedAt;
    @Column("original_price_xaf") private BigDecimal originalPriceXaf;
    @Column("adjusted_price_xaf") private BigDecimal adjustedPriceXaf;
    @Column("extra_km_fee") private BigDecimal extraKmFee;
    @Column("urgency_fee") private BigDecimal urgencyFee;
    @Column("pricing_reason") private String pricingReason;
    @Column("blockchain_tx_hash") private String blockchainTxHash;
    @CreatedDate @Column("created_at") private Instant createdAt;
    @LastModifiedDate @Column("updated_at") private Instant updatedAt;
}
