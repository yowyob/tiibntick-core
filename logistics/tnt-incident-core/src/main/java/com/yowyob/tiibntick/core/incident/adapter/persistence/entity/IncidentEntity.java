package com.yowyob.tiibntick.core.incident.adapter.persistence.entity;

import com.yowyob.tiibntick.core.incident.domain.enums.*;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * R2DBC entity mapped to the tnt_incidents table.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Table("tnt_incidents")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("reference_code")
    private String referenceCode;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    @Column("source_platform")
    private String sourcePlatform;

    @Column("mission_id")
    private UUID missionId;

    @Column("category")
    private String category;

    @Column("type")
    private String type;

    @Column("severity")
    private String severity;

    @Column("status")
    private String status;

    @Column("resolution_mode")
    private String resolutionMode;

    @Column("reported_by_actor_id")
    private UUID reportedByActorId;

    @Column("reported_by_role")
    private String reportedByRole;

    @Column("description")
    private String description;

    @Column("affected_parcel_ids")
    private String affectedParcelIds;

    @Column("multi_parcel_incident")
    private boolean multiParcelIncident;

    @Column("own_blockchain_chain_id")
    private String ownBlockchainChainId;

    @Column("reported_at")
    private Instant reportedAt;

    @Column("detected_at")
    private Instant detectedAt;

    @Column("acknowledged_at")
    private Instant acknowledgedAt;

    @Column("triaged_at")
    private Instant triagedAt;

    @Column("resolved_at")
    private Instant resolvedAt;

    @Column("closed_at")
    private Instant closedAt;

    @Column("last_escalation_level")
    private int lastEscalationLevel;

    @Column("auto_resolution_attempts")
    private int autoResolutionAttempts;

    @Column("inter_agency_involved")
    private boolean interAgencyInvolved;

    @Column("geo_lat")
    private Double geoLat;

    @Column("geo_lng")
    private Double geoLng;

    @Column("geo_address_label")
    private String geoAddressLabel;

    @Column("geo_nearest_hub_id")
    private UUID geoNearestHubId;

    @Column("geo_zone_risk_index")
    private Double geoZoneRiskIndex;

    @Column("sla_original_deadline")
    private Instant slaOriginalDeadline;

    @Column("sla_estimated_delay_minutes")
    private Long slaEstimatedDelayMinutes;

    @Column("sla_revised_deadline")
    private Instant slaRevisedDeadline;

    @Column("sla_breached")
    private Boolean slaBreached;

    @Column("sla_breach_minutes")
    private Long slaBreachMinutes;

    @Column("risk_global_score")
    private Double riskGlobalScore;

    @Column("risk_auto_resolution_recommended")
    private Boolean riskAutoResolutionRecommended;

    @Column("risk_recommended_action")
    private String riskRecommendedAction;

    @Column("compensation_estimated_xaf")
    private BigDecimal compensationEstimatedXaf;

    @Column("compensation_coverage_type")
    private String compensationCoverageType;

    // : FreelancerOrg responsibility context
    @Column("responsible_org_id")
    private String responsibleOrgId;

    @Column("responsible_org_type")
    private String responsibleOrgType;

    @Version
    private long version;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;
}
