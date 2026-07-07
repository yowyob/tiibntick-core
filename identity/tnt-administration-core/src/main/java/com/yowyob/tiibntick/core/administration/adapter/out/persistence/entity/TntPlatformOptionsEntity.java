package com.yowyob.tiibntick.core.administration.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity for TiiBnTick platform options.
 * Author: MANFOUO Braun
 */
@Table(schema = "administration", name = "tnt_platform_options")
public record TntPlatformOptionsEntity(
        @Id @Column("id") UUID id,
        @Column("tenant_id") UUID tenantId,
        @Column("blockchain_enabled") boolean blockchainEnabled,
        @Column("smart_dispute_resolution_enabled") boolean smartDisputeResolutionEnabled,
        @Column("blockchain_network") String blockchainNetwork,
        @Column("freelancer_mode_enabled") boolean freelancerModeEnabled,
        @Column("require_freelancer_approval") boolean requireFreelancerApproval,
        @Column("max_freelancer_concurrent_missions") int maxFreelancerConcurrentMissions,
        @Column("point_relais_mode_enabled") boolean pointRelaisModeEnabled,
        @Column("relay_point_max_storage_hours") int relayPointMaxStorageHours,
        @Column("announcement_marketplace_enabled") boolean announcementMarketplaceEnabled,
        @Column("max_courier_announcement_responses") int maxCourierAnnouncementResponses,
        @Column("tva_rate") BigDecimal tvaRate,
        @Column("default_currency") String defaultCurrency,
        @Column("dispute_management_enabled") boolean disputeManagementEnabled,
        @Column("dispute_filing_window_days") int disputeFilingWindowDays,
        @Column("freelancer_org_mode_enabled") boolean freelancerOrgModeEnabled,
        @Column("max_freelancer_org_fleet_size") int maxFreelancerOrgFleetSize,
        @Column("billing_templates_enabled") boolean billingTemplatesEnabled,
        @Column("max_billing_template_dsl_level") String maxBillingTemplateDslLevel,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {
}