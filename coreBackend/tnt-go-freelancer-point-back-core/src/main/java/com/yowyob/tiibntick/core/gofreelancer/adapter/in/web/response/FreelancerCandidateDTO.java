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
}
