package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.linkback.application.port.in.result.NetworkNodeProfileResult;

public final class NetworkNodeProfileResponseMapper {

    private NetworkNodeProfileResponseMapper() {
    }

    public static NetworkNodeProfileResponse toResponse(NetworkNodeProfileResult result) {
        return new NetworkNodeProfileResponse(
                NetworkNodeResponseMapper.toResponse(result.node()),
                result.displayName(),
                result.phoneNumber(),
                result.email(),
                result.rating(),
                result.reviewCount(),
                result.deliveryCount(),
                result.activeFlows(),
                result.containingZoneIds(),
                result.endorsementCount()
        );
    }
}
