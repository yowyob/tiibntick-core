package com.yowyob.tiibntick.core.incident.adapter.web.dto;

import com.yowyob.tiibntick.core.incident.domain.enums.IncidentDeliveryOutcome;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentResponsibleParty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Row I23 (QO-14): the manager proposes a resolution to the declaring courier. */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ProposeResolutionRequest {
    /** RESUMED or FAILED. */
    @NotNull IncidentDeliveryOutcome deliveryOutcome;
    IncidentResponsibleParty responsibleParty;
    String note;
}
