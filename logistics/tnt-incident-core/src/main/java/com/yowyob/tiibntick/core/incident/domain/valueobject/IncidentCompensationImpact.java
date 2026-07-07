package com.yowyob.tiibntick.core.incident.domain.valueobject;

import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.CoverageType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Estimated financial impact and applicable coverage for an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value
@Builder
public class IncidentCompensationImpact {
    UUID incidentId;
    BigDecimal estimatedAmountXAF;
    String currency;
    UUID responsiblePartyId;
    ActorRole responsiblePartyRole;
    CoverageType coverageType;
    BigDecimal platformDeductible;
    BigDecimal netPlatformLiability;

    public boolean hasCoverage() {
        return coverageType != CoverageType.NO_COVERAGE;
    }
}
