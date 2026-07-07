package com.yowyob.tiibntick.core.incident.adapter.persistence.entity;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
/**
 * R2DBC entity mapped to the tnt_incident_evidences table.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Table("tnt_incident_evidences")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class IncidentEvidenceEntity implements Persistable<UUID> {
    @Id private UUID id;

    @Transient
    private boolean isNew;
    @Column("incident_id") private UUID incidentId;
    @Column("evidence_type") private String evidenceType;
    @Column("file_url") private String fileUrl;
    @Column("mime_type") private String mimeType;
    @Column("captured_at") private Instant capturedAt;
    @Column("captured_by_actor_id") private UUID capturedByActorId;
    @Column("captured_by_role") private String capturedByRole;
    @Column("validated") private boolean validated;
    @Column("validated_at") private Instant validatedAt;
    @Column("validated_by_actor_id") private UUID validatedByActorId;
    @Column("blockchain_tx_hash") private String blockchainTxHash;
    @Column("sha256_checksum") private String sha256Checksum;
    @Column("latitude") private Double latitude;
    @Column("longitude") private Double longitude;
    @CreatedDate @Column("created_at") private Instant createdAt;
    @LastModifiedDate @Column("updated_at") private Instant updatedAt;
}
