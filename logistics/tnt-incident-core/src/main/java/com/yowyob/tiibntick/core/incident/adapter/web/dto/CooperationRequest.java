package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import com.yowyob.tiibntick.core.incident.domain.enums.CooperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;
/**
 * Request DTO for initiating an inter-agency cooperation.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class CooperationRequest {
    @NotNull UUID respondingAgencyId;
    @NotNull CooperationType cooperationType;
    @NotBlank String details;
    @NotNull UUID requestedByActorId;
}
