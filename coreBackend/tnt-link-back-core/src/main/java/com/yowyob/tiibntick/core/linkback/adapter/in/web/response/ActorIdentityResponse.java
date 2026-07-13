package com.yowyob.tiibntick.core.linkback.adapter.in.web.response;

import java.util.UUID;

public record ActorIdentityResponse(
        UUID actorId,
        String displayName,
        String phoneNumber,
        String email
) {
}
