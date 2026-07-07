package com.yowyob.tiibntick.core.organization.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for sub-deliverer associations of a FreelancerOrganization.
 *
 * <p>Maps to the {@code tnt_freelancer_sub_deliverer} table.
 * Each row represents one {@link com.yowyob.tiibntick.core.organization.domain.vo.AssociatedDelivererRef}.
 *
 * <p>Key columns:
 * <ul>
 *   <li>{@code freelancer_org_id}  — FK to {@code tnt_freelancer_organization.id}.</li>
 *   <li>{@code deliverer_actor_id} — Actor UUID from tnt-actor-core.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tnt_freelancer_sub_deliverer")
public class FreelancerSubDelivererEntity implements Persistable<UUID> {

    /** Synthetic primary key. */
    @Id
    @Column("id")
    private UUID id;

    @Transient
    private boolean isNew;

    /** FK to the owning FreelancerOrganization. */
    @Column("freelancer_org_id")
    private UUID freelancerOrgId;

    /** Sub-deliverer actor UUID (from tnt-actor-core). */
    @Column("deliverer_actor_id")
    private UUID delivererActorId;

    /** Association status enum name. */
    @Column("association_status")
    private String associationStatus;

    /** Commission rate fraction (0.0000–1.0000). */
    @Column("commission_rate")
    private BigDecimal commissionRate;

    /** Timestamp when the association became ACTIVE (nullable). */
    @Column("associated_since")
    private Instant associatedSince;

    /** Timestamp when the association was terminated (nullable). */
    @Column("terminated_at")
    private Instant terminatedAt;

    /** Record creation timestamp (UTC). */
    @Column("created_at")
    private Instant createdAt;
}
