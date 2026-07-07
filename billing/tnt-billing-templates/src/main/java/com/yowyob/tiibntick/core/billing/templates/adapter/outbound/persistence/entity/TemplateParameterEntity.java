package com.yowyob.tiibntick.core.billing.templates.adapter.outbound.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Spring Data R2DBC entity for the {@code billing_template_parameters} table.
 *
 * <p>Each row represents one adjustable parameter belonging to a {@link PolicyTemplateEntity}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("billing_template_parameters")
public class TemplateParameterEntity implements Persistable<UUID> {

    /** Primary key — UUID. */
    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    /** FK to billing_policy_templates(id). */
    @Column("template_id")
    private UUID templateId;

    /** Technical key (e.g. basePrice, perKmRate). */
    @Column("parameter_key")
    private String parameterKey;

    /** French display label. */
    @Column("label_fr")
    private String labelFr;

    /** English display label. */
    @Column("label_en")
    private String labelEn;

    /** Default value as string. */
    @Column("default_value")
    private String defaultValue;

    /** Minimum acceptable value (nullable). */
    @Column("min_value")
    private String minValue;

    /** Maximum acceptable value (nullable). */
    @Column("max_value")
    private String maxValue;

    /** Display unit (XAF, %, km, kg, h, x). */
    @Column("unit")
    private String unit;

    /** Parameter type enum name (MONEY, PERCENTAGE, INTEGER, DECIMAL, BOOLEAN, MULTIPLIER). */
    @Column("parameter_type")
    private String parameterType;

    /** Contextual help text. */
    @Column("help_text")
    private String helpText;
}
