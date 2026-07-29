package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayPointDetailsResponse {
    private UUID id;
    private UUID freelancerId;
    // Owner info
    private String ownerFirstName;
    private String ownerLastName;
    private String ownerEmail;
    private String ownerPhone;
    // RelayPoint info
    private String name;
    private String status;
    private Boolean isActive;
    private String storefrontPhoto;
    private String shopPhoto;
    private UUID addressId;
    private Double rating;
    // Subscription
    private String subscriptionPlan;
    private String subscriptionStatus;
    private Integer depositsUsed;
    private Integer maxDeposits;
    // Storage space dimensions
    /** Length of the storage space (hangar) in the unit defined by storageDimensionUnit. */
    private Double storageLength;
    private Double storageWidth;
    private Double storageHeight;
    /** Unit for storage dimensions: "m", "cm", "mm", "in". */
    private String storageDimensionUnit;
    /**
     * Total storage capacity in m³, derived from dimensions + unit.
     * Computed server-side for convenience; null when dimensions not set.
     */
    private Double storageTotalM3;
    // Timestamps
    private String createdAt;
    private String updatedAt;
}
