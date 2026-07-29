package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.Data;

import java.util.UUID;

@Data
public class EvaluationDTO {
    private UUID deliveryId;
    private UUID evaluatorId;
    private UUID evaluatedId;
    private Integer rating;
    private String comment;
    private String type; // CLIENT_TO_DP, DP_TO_CLIENT, CLIENT_TO_RP, RP_TO_CLIENT
}
