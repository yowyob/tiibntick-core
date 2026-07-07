package com.yowyob.tiibntick.core.delivery.domain.model.aggregate;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsClass;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root representing a registered delivery person (livreur).
 * Encapsulates logistics vehicle metadata, capacity, and real-time position.
 * This entity feeds the VRP solver as a "vehicle" with capacity constraints.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public class DeliveryPerson {

    private final UUID id;
    private final UUID tenantId;
    private final UUID actorId;

    // Vehicle information
    private final LogisticsType logisticsType;
    private final LogisticsClass logisticsClass;

    /**
     * Maximum cargo capacity in kg — used as VRP vehicle capacity.
     */
    private final double tankCapacity;
    private final double grossFloor;
    private final int totalSeatNumber;
    private String color;
    private final String commercialRegisterNumber;

    // Performance metrics
    private int remainingDeliveries;
    private int failedDeliveries;
    private int totalDeliveries;

    // Real-time location (updated on each GPS ping)
    private GeoCoordinates currentLocation;
    private Instant locationUpdatedAt;

    private DeliveryPersonStatus status;

    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    /**
     * Factory method for registering a new delivery person.
     */
    public static DeliveryPerson register(UUID tenantId,
                                           UUID actorId,
                                           LogisticsType logisticsType,
                                           LogisticsClass logisticsClass,
                                           double tankCapacity,
                                           double grossFloor,
                                           int totalSeatNumber,
                                           String color,
                                           String commercialRegisterNumber) {
        if (tankCapacity <= 0) {
            throw new DeliveryDomainException("Tank capacity must be positive");
        }
        return DeliveryPerson.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .actorId(actorId)
                .logisticsType(logisticsType)
                .logisticsClass(logisticsClass)
                .tankCapacity(tankCapacity)
                .grossFloor(grossFloor)
                .totalSeatNumber(totalSeatNumber)
                .color(color)
                .commercialRegisterNumber(commercialRegisterNumber)
                .remainingDeliveries(0)
                .failedDeliveries(0)
                .totalDeliveries(0)
                .status(DeliveryPersonStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Updates the real-time GPS position of the delivery person.
     */
    public void updateLocation(GeoCoordinates location) {
        this.currentLocation = location;
        this.locationUpdatedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Approves the delivery person for platform operations.
     */
    public void approve() {
        if (status != DeliveryPersonStatus.PENDING) {
            throw new DeliveryDomainException("Only PENDING delivery persons can be approved");
        }
        this.status = DeliveryPersonStatus.APPROVED;
        this.updatedAt = Instant.now();
    }

    /**
     * Suspends the delivery person from accepting new deliveries.
     */
    public void suspend() {
        this.status = DeliveryPersonStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    /**
     * Returns {@code true} if this delivery person can accept new deliveries.
     */
    public boolean isAvailable() {
        return status == DeliveryPersonStatus.APPROVED;
    }

    /**
     * Records a completed delivery (increments counters).
     */
    public void recordDeliveryCompleted() {
        this.totalDeliveries++;
        this.updatedAt = Instant.now();
    }

    /**
     * Records a failed delivery attempt.
     */
    public void recordDeliveryFailed() {
        this.failedDeliveries++;
        this.totalDeliveries++;
        this.updatedAt = Instant.now();
    }
}
