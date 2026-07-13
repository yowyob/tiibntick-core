package com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "agency_org", name = "agency_settings")
public class AgencySettingsEntity {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    @Column("auto_assign_missions")
    private Boolean autoAssignMissions;

    @Column("allow_freelancer_association")
    private Boolean allowFreelancerAssociation;

    @Column("hub_retention_delay_hours")
    private Integer hubRetentionDelayHours;

    @Column("default_currency")
    private String defaultCurrency;

    @Column("default_commission_rate")
    private BigDecimal defaultCommissionRate;

    @Column("max_active_branches")
    private Integer maxActiveBranches;

    private String timezone;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    private Long version;
}
