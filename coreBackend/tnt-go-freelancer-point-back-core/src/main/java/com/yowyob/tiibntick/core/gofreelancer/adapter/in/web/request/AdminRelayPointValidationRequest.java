package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRelayPointValidationRequest {
    private UUID relayPointId;
    private boolean approved;
    private String reason;
}
