package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;
/**
 * Request DTO for escalating an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class EscalateRequest {
    @NotNull UUID escalatedByActorId;
    @NotNull ActorRole escalatedByRole;
    UUID targetActorId;
    @NotNull ActorRole targetRole;
    @NotBlank String reason;
    boolean triggerDispute;
    String fraudEvidence;
}
