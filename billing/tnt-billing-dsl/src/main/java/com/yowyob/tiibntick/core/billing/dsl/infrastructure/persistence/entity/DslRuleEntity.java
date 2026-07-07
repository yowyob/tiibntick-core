package com.yowyob.tiibntick.core.billing.dsl.infrastructure.persistence.entity;

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
import java.util.UUID;

/**
 * R2DBC persistence entity for {@code dsl_rule} table.
 * Actions are serialised to JSON in the {@code actions_json} column.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "billing", name = "dsl_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DslRuleEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("condition_expression")
    private String conditionExpression;

    @Column("action_expression")
    private String actionExpression;

    @Column("actions_json")
    private String actionsJson;

    @Column("priority")
    private int priority;

    @Column("active")
    private boolean active;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("policy_id")
    private UUID policyId;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
