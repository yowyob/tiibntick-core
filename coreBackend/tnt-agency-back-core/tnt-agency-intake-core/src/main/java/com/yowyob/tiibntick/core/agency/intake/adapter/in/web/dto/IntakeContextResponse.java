package com.yowyob.tiibntick.core.agency.intake.adapter.in.web.dto;

import java.util.UUID;

public record IntakeContextResponse(
        UUID agencyId, String agencyName,
        UUID branchId, String branchName, String branchAddress) {}
