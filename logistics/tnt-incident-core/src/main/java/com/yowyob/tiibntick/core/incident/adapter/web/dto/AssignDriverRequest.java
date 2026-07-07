package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;
/**
 * Request DTO for assigning a replacement driver.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AssignDriverRequest {
    @NotNull UUID replacementDriverId;
    @NotNull UUID replacementVehicleId;
    @NotNull UUID replacementAgencyId;
    @NotNull UUID assignedByActorId;
    boolean manualAssignment;
}
