package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for the {@code Parcel} aggregate.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_parcels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcelEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("weight_kg")
    private Double weightKg;

    @Column("width_cm")
    private Double widthCm;

    @Column("height_cm")
    private Double heightCm;

    @Column("length_cm")
    private Double lengthCm;

    @Column("fragile")
    private Boolean fragile;

    @Column("perishable")
    private Boolean perishable;

    @Column("description")
    private String description;

    @Column("photo_url")
    private String photoUrl;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Long version;
}
