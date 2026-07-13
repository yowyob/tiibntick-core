package com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC entity mapped to tnt_market.provider_reviews.
 *
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_market", value = "provider_reviews")
public class ProviderReviewEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private String tenantId;

    @Column("client_id")
    private UUID clientId;

    @Column("provider_id")
    private UUID providerId;

    @Column("listing_id")
    private UUID listingId;

    @Column("order_id")
    private UUID orderId;

    @Column("status")
    private String status;

    @Column("comment")
    private String comment;

    /** JSON array of ReviewTag enum names. */
    @Column("tags")
    private String tags;

    @Column("overall_score")
    private int overall;

    @Column("punctuality_score")
    private int punctuality;

    @Column("communication_score")
    private int communication;

    @Column("packaging_score")
    private int packaging;

    @Column("value_score")
    private int value;

    @Column("moderated_by")
    private UUID moderatedBy;

    @Column("moderated_at")
    private LocalDateTime moderatedAt;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private long version;
}
