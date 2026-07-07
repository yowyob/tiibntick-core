package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.List;
import java.util.UUID;
/**
 * Request DTO for reporting a new incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ReportIncidentRequest {
    @NotNull UUID tenantId;
    @NotNull UUID agencyId;
    @NotNull UUID missionId;
    @NotNull PlatformType platform;
    @NotNull IncidentType type;
    @NotBlank String description;
    @NotNull UUID reportedByActorId;
    @NotNull ActorRole reportedByRole;
    List<UUID> affectedParcelIds;
    Double currentLat;
    Double currentLng;
}
