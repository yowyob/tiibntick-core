package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;

public final class NetworkAlertResponseMapper {

    private NetworkAlertResponseMapper() {
    }

    public static NetworkAlertResponse toResponse(NetworkAlert alert) {
        return new NetworkAlertResponse(
                alert.getId(),
                alert.getReporterId(),
                alert.getType().name(),
                alert.getDescription(),
                alert.getLocation().latitude(),
                alert.getLocation().longitude(),
                alert.getSeverity().name(),
                alert.getStatus().name(),
                alert.getConfirmCount(),
                alert.getCreatedAt(),
                alert.getUpdatedAt(),
                alert.getResolvedAt()
        );
    }
}
