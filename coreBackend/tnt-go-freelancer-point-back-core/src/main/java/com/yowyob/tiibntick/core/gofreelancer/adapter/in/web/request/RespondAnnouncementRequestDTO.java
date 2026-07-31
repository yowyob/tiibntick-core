package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Freelancer respond / subscribe payload for a delivery-core announcement.
 *
 * @author MANFOUO BRAUN
 */
@Data
@NoArgsConstructor
public class RespondAnnouncementRequestDTO {
    private UUID freelancerId;
    private Instant estimatedArrivalTime;
    private String note;
    /** Required when announcement pricingMode is QUOTE_REQUEST. */
    private BigDecimal proposedPrice;
    private String proposedCurrency;
}
