package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import com.yowyob.tiibntick.core.linkback.domain.model.NodeStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateNodeStatusRequest(
        @NotNull NodeStatus status
) {
}
