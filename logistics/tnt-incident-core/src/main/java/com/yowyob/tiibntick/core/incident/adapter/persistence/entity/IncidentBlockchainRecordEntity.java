package com.yowyob.tiibntick.core.incident.adapter.persistence.entity;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
/**
 * R2DBC entity mapped to the tnt_incident_blockchain_records table.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Table("tnt_incident_blockchain_records")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class IncidentBlockchainRecordEntity implements Persistable<UUID> {
    @Id private UUID id;

    @Transient
    private boolean isNew;
    @Column("incident_id") private UUID incidentId;
    @Column("chain_id") private String chainId;
    @Column("block_index") private long blockIndex;
    @Column("previous_hash") private String previousHash;
    @Column("current_hash") private String currentHash;
    @Column("event_type") private String eventType;
    @Column("payload") private String payload;
    @Column("nonce") private long nonce;
    @Column("verified") private boolean verified;
    @CreatedDate @Column("created_at") private Instant createdAt;
}
