package com.yowyob.tiibntick.core.linkback.application.port.in.command;

import com.yowyob.tiibntick.core.linkback.domain.model.NodeRefType;

import java.util.UUID;

public record RegisterNetworkNodeCommand(
        UUID tenantId,
        NodeRefType refType,
        UUID refId,
        String description,
        String declaredZoneName,
        String declaredCity,
        Integer declaredCapacityParcels
) {
}
