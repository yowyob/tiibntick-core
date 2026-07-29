package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.Data;

@Data
public class PacketDTO {
    private Double weight;
    private Double width;
    private Double height;
    private Double length;
    private Boolean fragile;
    private String description;
    private String photoPacket;
    private Boolean isPerishable;
    private Double thickness;
    private String designation;
}
