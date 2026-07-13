package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import com.yowyob.tiibntick.core.linkback.domain.model.TrustLink;

public final class TrustLinkResponseMapper {

    private TrustLinkResponseMapper() {
    }

    public static TrustLinkResponse toResponse(TrustLink link) {
        return new TrustLinkResponse(link.getId(), link.getFromNodeId(), link.getToNodeId(), link.getCreatedAt());
    }
}
