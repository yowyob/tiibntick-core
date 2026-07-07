package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data R2DBC entity for the {@code billing_policy_templates} table.
 *
 * <p>This entity represents the main row of a billing policy template in the
 * database. Template parameters are stored in a separate table
 * ({@code billing_template_parameters}) and joined at the repository level.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("billing_policy_templates")
public class PolicyTemplateEntity implements Persistable<UUID> {

    /** Primary key — UUID. */
    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    /** Business key — e.g. TPL-BASE-STD. */
    @Column("template_code")
    private String templateCode;

    /** Display name. */
    @Column("name")
    private String name;

    /** Long description. */
    @Column("description")
    private String description;

    /**
     * Category stored as string (enum name).
     * e.g. BASE, SPECIALTY, LOYALTY, TIME, WEATHER, HUB, NETWORK, COMMISSION, MARKETPLACE
     */
    @Column("category")
    private String category;

    /**
     * JSON-serialized list of applicable owner type strings.
     * e.g. ["AGENCY","FREELANCER_ORG"]
     */
    @Column("applicable_to_json")
    private String applicableToJson;

    /**
     * The default DSL rule string generated for this template.
     * Stored for documentation/audit and used as fallback in DslRuleGeneratorService.
     */
    @Column("default_dsl_rules")
    private String defaultDslRules;

    /** Whether the template is currently active in the catalog. */
    @Column("is_active")
    private boolean active;

    /** Timestamp of first save. */
    @Column("created_at")
    private Instant createdAt;

    /** Timestamp of last update. */
    @Column("updated_at")
    private Instant updatedAt;
}
