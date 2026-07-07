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
 * R2DBC persistence entity for {@code DeliveryPerson} aggregate.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_delivery_persons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPersonEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("actor_id")
    private UUID actorId;

    @Column("logistics_type")
    private String logisticsType;

    @Column("logistics_class")
    private String logisticsClass;

    @Column("tank_capacity")
    private Double tankCapacity;

    @Column("gross_floor")
    private Double grossFloor;

    @Column("total_seat_number")
    private Integer totalSeatNumber;

    @Column("color")
    private String color;

    @Column("commercial_register_number")
    private String commercialRegisterNumber;

    @Column("remaining_deliveries")
    private Integer remainingDeliveries;

    @Column("failed_deliveries")
    private Integer failedDeliveries;

    @Column("total_deliveries")
    private Integer totalDeliveries;

    @Column("current_latitude")
    private Double currentLatitude;

    @Column("current_longitude")
    private Double currentLongitude;

    @Column("location_updated_at")
    private Instant locationUpdatedAt;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    @Column("version")
    private Long version;
}
