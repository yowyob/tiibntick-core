package com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AgencySettingsResponse(
        UUID agencyId,
        Boolean autoAssignMissions,
        Boolean allowFreelancerAssociation,
        Integer hubRetentionDelayHours,
        String defaultCurrency,
        BigDecimal defaultCommissionRate,
        Integer maxActiveBranches,
        String timezone) {}
