package com.yowyob.tiibntick.core.incident.adapter.persistence.entity;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.*;
import java.time.Instant;
import java.util.UUID;
/**
 * R2DBC entity mapped to the tnt_parcel_incident_links table.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Table("tnt_parcel_incident_links")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class ParcelIncidentLinkEntity implements Persistable<UUID> {
    @Id @Column("parcel_id") private UUID parcelId;

    @Transient private boolean isNew;

    @Override public UUID getId() { return parcelId; }
    @Column("incident_id") private UUID incidentId;
    @Column("parcel_chain_id") private String parcelChainId;
    @Column("parcel_chain_tail_hash") private String parcelChainTailHash;
    @Column("incident_chain_id") private String incidentChainId;
    @Column("incident_chain_head_hash") private String incidentChainHeadHash;
    @Column("incident_chain_tail_hash") private String incidentChainTailHash;
    @Column("linked_at") private Instant linkedAt;
    @Column("resumed_at") private Instant resumedAt;
    @Column("resumption_confirmed") private boolean resumptionConfirmed;
}
