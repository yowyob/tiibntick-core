package com.yowyob.tiibntick.core.billing.cost.adapter.out.persistence.entity;

import com.yowyob.tiibntick.core.billing.cost.domain.model.FleetCostParameters;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC persistence entity for {@link FleetCostParameters}.
 * Maps to the {@code fleet_cost_parameters} table.
 *
 * @author MANFOUO Braun
 */
@Table("fleet_cost_parameters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetCostParametersEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("owner_org_id")
    private String ownerOrgId;

    @Column("fuel_price_liter_xaf")
    private BigDecimal fuelPriceLiterXaf;

    @Column("vehicle_wear_rate_per_km")
    private BigDecimal vehicleWearRatePerKm;

    @Column("time_value_per_hour")
    private BigDecimal timeValuePerHour;

    @Column("terrain_degradation_factor")
    private BigDecimal terrainDegradationFactor;

    @Column("rain_penalty_factor")
    private BigDecimal rainPenaltyFactor;

    @Column("auto_update_fuel_price")
    private Boolean autoUpdateFuelPrice;

    @Column("last_fuel_price_update_at")
    private LocalDateTime lastFuelPriceUpdateAt;

    @Column("updated_at")
    private Instant updatedAt;

    public static FleetCostParametersEntity fromDomain(FleetCostParameters domain) {
        return FleetCostParametersEntity.builder()
                .id(UUID.randomUUID())
                .ownerOrgId(domain.ownerOrgId())
                .fuelPriceLiterXaf(domain.fuelPriceLiterXAF())
                .vehicleWearRatePerKm(domain.vehicleWearRatePerKm())
                .timeValuePerHour(domain.timeValuePerHour())
                .terrainDegradationFactor(domain.terrainDegradationFactor())
                .rainPenaltyFactor(domain.rainPenaltyFactor())
                .autoUpdateFuelPrice(domain.autoUpdateFuelPrice())
                .lastFuelPriceUpdateAt(domain.lastFuelPriceUpdateAt())
                .updatedAt(Instant.now())
                .build();
    }

    public FleetCostParameters toDomain() {
        return FleetCostParameters.builder()
                .ownerOrgId(ownerOrgId)
                .fuelPriceLiterXAF(fuelPriceLiterXaf)
                .vehicleWearRatePerKm(vehicleWearRatePerKm)
                .timeValuePerHour(timeValuePerHour)
                .terrainDegradationFactor(terrainDegradationFactor)
                .rainPenaltyFactor(rainPenaltyFactor)
                .autoUpdateFuelPrice(autoUpdateFuelPrice)
                .lastFuelPriceUpdateAt(lastFuelPriceUpdateAt)
                .build();
    }
}
