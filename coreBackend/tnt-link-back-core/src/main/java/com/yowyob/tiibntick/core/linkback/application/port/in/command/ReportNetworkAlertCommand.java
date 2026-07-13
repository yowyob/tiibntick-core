package com.yowyob.tiibntick.core.linkback.application.port.in.command;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertSeverity;
import com.yowyob.tiibntick.core.linkback.domain.model.AlertType;

import java.util.UUID;

public record ReportNetworkAlertCommand(
        UUID tenantId,
        UUID reporterId,
        AlertType type,
        String description,
        GeoPoint location,
        AlertSeverity severity
) {
}
