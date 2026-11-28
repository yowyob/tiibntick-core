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
    /** Required for a manager; ignored when the declaring courier closes his own non-blocking incident (I6). */
    ResolutionMode resolutionMode;
    String resolutionNotes;
    /** Lot L3: RESUMED or FAILED, mandatory for a blocking incident (RESUMED by default otherwise). */
    com.yowyob.tiibntick.core.incident.domain.enums.IncidentDeliveryOutcome deliveryOutcome;
    /** Lot L3: payer of a return [B.6], optional. */
    com.yowyob.tiibntick.core.incident.domain.enums.IncidentResponsibleParty responsibleParty;
}
