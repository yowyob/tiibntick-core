package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Lot C-52 — an invitation as read by the client (its need) or by the invited courier. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NeedInvitationDTO {
    private UUID id;
    private UUID deliveryNeedId;
    /** {@code gofp_freelancers.id}, the id the candidates list returns. */
    private UUID freelancerId;
    /** OPEN, ACCEPTED, DECLINED or EXPIRED (an open invitation past its deadline reads EXPIRED). */
    private String status;
    private Instant expiresAt;
    /** Seconds left before expiry while OPEN, 0 otherwise. */
    private long remainingSeconds;
    private Double estimatedPrice;
    private String declineReason;
    private Instant createdAt;
    private Instant respondedAt;
    /** The need (addresses, parcel), filled for the courier's own list. */
    private DeliveryNeedResponseDTO need;
}
