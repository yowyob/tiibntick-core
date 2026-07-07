package com.yowyob.tiibntick.core.incident.adapter.persistence.entity;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
/**
 * R2DBC entity mapped to the tnt_incident_inter_agency_cooperations table.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Table("tnt_incident_inter_agency_cooperations")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class IncidentInterAgencyCooperationEntity implements Persistable<UUID> {
    @Id private UUID id;

    @Transient
    private boolean isNew;
    @Column("incident_id") private UUID incidentId;
    @Column("requesting_agency_id") private UUID requestingAgencyId;
    @Column("responding_agency_id") private UUID respondingAgencyId;
    @Column("cooperation_type") private String cooperationType;
    @Column("status") private String status;
    @Column("requested_at") private Instant requestedAt;
    @Column("accepted_at") private Instant acceptedAt;
    @Column("completed_at") private Instant completedAt;
    @Column("rejected_at") private Instant rejectedAt;
    @Column("rejection_reason") private String rejectionReason;
    @Column("request_details") private String requestDetails;
    @Column("response_details") private String responseDetails;
    @Column("blockchain_tx_hash") private String blockchainTxHash;
    @Column("notified_requesting_driver") private boolean notifiedToRequestingDriver;
    @Column("notified_responding_driver") private boolean notifiedToRespondingDriver;
    @CreatedDate @Column("created_at") private Instant createdAt;
    @LastModifiedDate @Column("updated_at") private Instant updatedAt;
}
