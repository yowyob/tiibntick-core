package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import java.time.LocalTime;
import java.time.DayOfWeek;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for opening hours.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningHoursDTO {
    private DayOfWeek dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean isClosed;
}
