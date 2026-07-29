package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a freelancer deliverer's GPS position.
 * Feeds the centralized tnt-realtime-core GPS ping pipeline.
 *
 * @author François-Charles ATANGA
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerLocationUpdateRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0",  message = "Latitude must be <= 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
    private Double longitude;

    /** Current speed in km/h. Defaults to 0.0 if not provided. */
    private double speedKmh = 0.0;

    /** Heading in degrees [0, 360]. Defaults to 0.0 if not provided. */
    @DecimalMin(value = "0.0",   message = "Bearing must be >= 0")
    @DecimalMax(value = "360.0", message = "Bearing must be <= 360")
    private double bearing = 0.0;

    /** GPS accuracy in meters. Defaults to 0.0 if not provided. */
    private double accuracy = 0.0;

    /** Active mission UUID string. Optional — null for pings outside a mission. */
    private String missionId;

    /**
     * FreelancerOrg UUID string. Optional — when set, the ping is also broadcast
     * to the fleet tracking topic {@code /topic/fleet/{freelancerOrgId}}.
     */
    private String freelancerOrgId;
}
