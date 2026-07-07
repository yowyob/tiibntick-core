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
 * Spring Data R2DBC entity for the {@code billing_custom_policy_templates} table.
 *
 * <p>Stores personal template snapshots saved by actors for reuse.
 * The customized parameter values are stored as a JSON column.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("billing_custom_policy_templates")
public class CustomPolicyTemplateEntity implements Persistable<UUID> {

    /** Primary key — UUID. */
    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    /** The actor UUID who owns this custom template. */
    @Column("owner_actor_id")
    private String ownerActorId;

    /** The type of the owning actor (enum name). */
    @Column("owner_type")
    private String ownerType;

    /** User-defined display name. */
    @Column("name")
    private String name;

    /** Source catalog template code (nullable). */
    @Column("source_template_code")
    private String sourceTemplateCode;

    /** UUID of the last generated BillingPolicy (nullable). */
    @Column("last_generated_policy_id")
    private UUID lastGeneratedPolicyId;

    /**
     * JSON-serialized map of parameter key → custom value.
     * e.g. {"basePrice":"700","perKmRate":"60"}
     */
    @Column("customized_parameters_json")
    private String customizedParametersJson;

    /** Creation timestamp. */
    @Column("created_at")
    private Instant createdAt;

    /** Last update timestamp. */
    @Column("updated_at")
    private Instant updatedAt;
}
