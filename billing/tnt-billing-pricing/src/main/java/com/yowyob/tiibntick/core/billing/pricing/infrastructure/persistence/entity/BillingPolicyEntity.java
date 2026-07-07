package com.yowyob.tiibntick.core.billing.pricing.infrastructure.persistence.entity;

import com.yowyob.tiibntick.core.billing.pricing.domain.model.enums.PolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@code pricing.billing_policy} table.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #ownerType} — VARCHAR policy owner type</li>
 *   <li>{@link #ownerActorId} — VARCHAR UUID string of the policy owner</li>
 *   <li>{@link #isFromTemplate} — BOOLEAN template flag</li>
 *   <li>{@link #templateCode} — VARCHAR source template code</li>
 *   <li>{@link #dslAccessLevel} — VARCHAR DSL access level</li>
 *   <li>{@link #specialSurchargesJson} — TEXT JSON special surcharges</li>
 *   <li>{@link #hubStorageRulesJson} — TEXT JSON hub storage rules</li>
 *   <li>{@link #networkTransitRulesJson} — TEXT JSON network transit rules</li>
 *   <li>{@link #fleetCostParametersJson} — TEXT JSON fleet cost parameters</li>
 * </ul>
 * See migration {@code V002__add_policy_owner_and_advanced_rules.yaml}.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "pricing", name = "billing_policy")
public class BillingPolicyEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    /** Set to true for new entities so Spring Data R2DBC issues INSERT instead of UPDATE. */
    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("agency_id")
    private UUID agencyId;

    private String name;
    private String description;

    @Column("pricing_rules_json")
    private String pricingRulesJson;

    @Column("surcharge_rules_json")
    private String surchargeRulesJson;

    @Column("promotions_json")
    private String promotionsJson;

    @Column("loyalty_rules_json")
    private String loyaltyRulesJson;

    @Column("commission_rules_json")
    private String commissionRulesJson;

    @Column("platform_fee_rule_json")
    private String platformFeeRuleJson;

    @Column("is_default")
    private boolean isDefault;

    private PolicyStatus status;

    @Column("valid_from")
    private LocalDate validFrom;

    @Column("valid_to")
    private LocalDate validTo;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    //  columns ──────────────────────────────────────────────────────────

    /** Policy owner type (AGENCY, FREELANCER_ORG, POINT, LINK, ADMIN, MARKET). */
    @Column("owner_type")
    private String ownerType;

    /** UUID string of the policy owner actor. Null for legacy agency-only policies. */
    @Column("owner_actor_id")
    private String ownerActorId;

    /** Whether this policy was created from a policy template. */
    @Column("is_from_template")
    private boolean isFromTemplate;

    /** Code of the source policy template. Null for scratch-built policies. */
    @Column("template_code")
    private String templateCode;

    /** DSL access level: FULL, SIMPLIFIED, or NONE. Default FULL. */
    @Column("dsl_access_level")
    private String dslAccessLevel;

    /** JSON array of SpecialSurchargeRule objects. */
    @Column("special_surcharges_json")
    private String specialSurchargesJson;

    /** JSON array of HubStorageRule objects. */
    @Column("hub_storage_rules_json")
    private String hubStorageRulesJson;

    /** JSON array of NetworkTransitRule objects. */
    @Column("network_transit_rules_json")
    private String networkTransitRulesJson;

    /** JSON object of FleetCostParameters. */
    @Column("fleet_cost_parameters_json")
    private String fleetCostParametersJson;
}
