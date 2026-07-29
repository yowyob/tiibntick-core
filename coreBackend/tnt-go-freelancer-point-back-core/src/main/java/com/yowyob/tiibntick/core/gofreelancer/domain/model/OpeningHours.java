package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Represents the opening hours of a RelayPoint.
 * Owned by tnt-go-freelancer-point-back-core (Layer 6).
 * TOPSIS uses these hours as a matching criterion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("opening_hours")
public class OpeningHours {

    @Id
    @Column("id")
    private UUID id;

    /** FK to the relay point (logistics entity) in Layer 6 */
    @Column("relay_point_id")
    private UUID relayPointId;

    @Column("day_of_week")
    private DayOfWeek dayOfWeek;

    @Column("open_time")
    private LocalTime openTime;

    @Column("close_time")
    private LocalTime closeTime;

    @Column("is_closed")
    private Boolean isClosed;

    /**
     * Returns true if this slot is currently open at the given time.
     */
    public boolean isOpenAt(LocalTime time) {
        if (Boolean.TRUE.equals(isClosed) || openTime == null || closeTime == null) {
            return false;
        }
        return !time.isBefore(openTime) && time.isBefore(closeTime);
    }
}
