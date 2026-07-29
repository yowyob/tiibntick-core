package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import java.time.DayOfWeek;
import java.time.LocalTime;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for delivery person availability schedule.
 * Allows a delivery person to define their working hours per day.
 *
 * @author TiiBnTickTeam
 * @date 07/07/2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreelancerAvailabilityDTO {

    @NotNull(message = "Day of week is required")
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private Boolean isDayOff;
}
