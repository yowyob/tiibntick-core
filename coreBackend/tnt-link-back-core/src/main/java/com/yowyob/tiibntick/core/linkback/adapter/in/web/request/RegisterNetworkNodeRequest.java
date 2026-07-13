package com.yowyob.tiibntick.core.linkback.adapter.in.web.request;

import com.yowyob.tiibntick.core.linkback.domain.model.NodeRefType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegisterNetworkNodeRequest(
        @NotNull NodeRefType refType,
        @NotNull UUID refId,
        String description,
        String declaredZoneName,
        String declaredCity,
        Integer declaredCapacityParcels
) {
}
