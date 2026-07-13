package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import com.yowyob.tiibntick.core.linkback.domain.model.AlertSeverity;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertType;
import jakarta.validation.constraints.NotNull;

public record ReportNetworkAlertRequest(
        @NotNull AlertType type,
        String description,
        @NotNull Double latitude,
        @NotNull Double longitude,
        AlertSeverity severity
) {
}
