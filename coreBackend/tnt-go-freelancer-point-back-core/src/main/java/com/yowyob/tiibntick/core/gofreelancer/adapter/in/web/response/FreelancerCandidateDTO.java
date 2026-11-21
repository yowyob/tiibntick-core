package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerCandidateDTO {
    private UUID freelancerId;
    private String lastName;
    private String firstName;
    private Double rating;
    private Double estimatedPrice;
    private String priceBreakdown;
    /**
     * Lot C-52 — {@code AVAILABLE}: can be invited; {@code BUSY}: has an unfinished delivery, shown
     * but not invitable. Paused couriers are never listed.
     */
    private String status;

    // ── Lot C-54 — courier card. The profile creation date is never part of it. ──
    private String phone;
    /** Canonical core type (MOTORBIKE, TRICYCLE, CAR, TRUCK, BUS…), null when the courier declared none. */
    private String vehicleType;
    private String vehiclePlate;
    private java.util.List<String> vehiclePhotoUrls;
    /** Parcels this courier brought to DELIVERED. */
    private Long deliveredCount;
    /** {@code NEW}, {@code CONFIRMED}, {@code LOYAL} or null. */
    private String badge;

    public static final String AVAILABLE = "AVAILABLE";
    public static final String BUSY = "BUSY";
}
