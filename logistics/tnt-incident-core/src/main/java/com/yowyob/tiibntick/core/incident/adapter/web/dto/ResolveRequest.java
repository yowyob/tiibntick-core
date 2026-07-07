package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import com.yowyob.tiibntick.core.incident.domain.enums.ResolutionMode;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;
/**
 * Request DTO for resolving an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ResolveRequest {
    @NotNull UUID resolvedByActorId;
    @NotNull ResolutionMode resolutionMode;
    String resolutionNotes;
}
