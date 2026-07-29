package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Summary of a user's registered profiles within the system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileSummaryDTO {

    private UUID coreUserId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String cniNumber;
    private String nui;
    private String profilePhotoUrl;
    

    private boolean isClient;
    private boolean isFreelancer;
    private boolean isRelayPoint;

    // IDs for fast navigation if needed
    private UUID clientId;
    private UUID freelancerId;
    private UUID relayPointId;
}
