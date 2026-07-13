package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofp.packets")
public class PacketEntity {

    @Id
    private UUID    id;
    private Double  weight;
    private Double  width;
    private Double  height;
    private Double  length;
    private Double  thickness;
    private Boolean fragile;

    @Column("is_perishable")
    private Boolean isPerishable;

    private String designation;
    private String description;

    @Column("photo_packet")
    private String photoPacket;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
