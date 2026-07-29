package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleClass;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a vehicle or store entry.
 *
 * @author TiiBnTickTeam
 * @date 08/07/2026
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleCreateRequest {
    private VehicleType vehicleType;
    private VehicleClass vehicleClass;
    private String plateNumber;
    private Double length;
    private Double width;
    private Double height;
    private String unit;
    private String backPhoto;
    private String frontPhoto;
    private Double tankCapacity;
    private Integer totalSeatNumber;
    private String color;
}
