package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * What a delivery person accepts to carry and when.
 *
 * <p>An absent list or bound means "no constraint" (everything accepted / always available / any
 * weight), so a freelancer who never touches the screen is never filtered out.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerEligibilityDTO {

    @Valid
    private List<Slot> availability;

    /** PackageType codes the freelancer accepts (STANDARD, DOCUMENT, FRAGILE, PERISHABLE, OVERSIZED…). */
    private List<String> acceptedPackages;

    @Min(0)
    private Double minWeightKg;

    @Min(0)
    private Double maxWeightKg;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Slot {
        /** MONDAY … SUNDAY. */
        @Pattern(regexp = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY")
        private String day;
        @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d")
        private String start;
        @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d")
        private String end;
        private boolean active;
    }
}
