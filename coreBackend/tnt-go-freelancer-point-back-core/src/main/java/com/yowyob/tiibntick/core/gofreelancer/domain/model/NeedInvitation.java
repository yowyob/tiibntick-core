package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A client proposes one open delivery need to one courier ({@code gofp_freelancers.id}), who accepts
 * or declines before {@link #expiresAt}. Accepting runs the same election path as choosing an
 * applicant; declining or letting it expire leaves the need's announcement open for others.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofp_need_invitations")
public class NeedInvitation {

    public static final String OPEN = "OPEN";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @Column("id")
    private UUID id;

    @Column("delivery_need_id")
    private UUID deliveryNeedId;

    /** {@code gofp_freelancers.id}. */
    @Column("freelancer_id")
    private UUID freelancerId;

    /** Owner of the delivery need (token {@code userId}). */
    @Column("invited_by")
    private UUID invitedBy;

    @Column("status")
    private String status;

    /** Server price shown to the courier when invited (informative; acceptance recomputes it). */
    @Column("estimated_price")
    private Double estimatedPrice;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("decline_reason")
    private String declineReason;

    @Column("created_at")
    private Instant createdAt;

    @Column("responded_at")
    private Instant respondedAt;

    /** Open and past its deadline at {@code now}. */
    public boolean isExpiredAt(Instant now) {
        return OPEN.equals(status) && expiresAt != null && !now.isBefore(expiresAt);
    }

    /** Status as seen by a reader at {@code now}: an open invitation past its deadline reads EXPIRED. */
    public String effectiveStatus(Instant now) {
        return isExpiredAt(now) ? EXPIRED : status;
    }
}
