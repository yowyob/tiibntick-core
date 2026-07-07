package com.yowyob.tiibntick.core.incident.adapter.persistence.entity;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
/**
 * R2DBC entity mapped to the tnt_incident_event_logs table.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Table("tnt_incident_event_logs")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class IncidentEventLogEntity implements Persistable<UUID> {
    @Id private UUID id;

    @Transient
    private boolean isNew;
    @Column("incident_id") private UUID incidentId;
    @Column("event_type") private String eventType;
    @Column("occurred_at") private Instant occurredAt;
    @Column("performed_by_actor_id") private UUID performedByActorId;
    @Column("performed_by_role") private String performedByRole;
    @Column("payload") private String payload;
    @Column("blockchain_tx_hash") private String blockchainTxHash;
    @Column("blockchain_chain_ref") private String blockchainChainRef;
    @Column("written_on_parcel_chain") private boolean writtenOnParcelChain;
    @Column("written_on_incident_chain") private boolean writtenOnIncidentChain;
    @CreatedDate @Column("created_at") private Instant createdAt;
}
