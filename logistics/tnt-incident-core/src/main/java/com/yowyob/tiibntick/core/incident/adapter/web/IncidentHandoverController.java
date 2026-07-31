package com.yowyob.tiibntick.core.incident.adapter.web;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.incident.application.command.ConfirmHandoverCommand;
import com.yowyob.tiibntick.core.incident.application.command.AssignReplacementDriverCommand;
import com.yowyob.tiibntick.core.incident.adapter.web.dto.AssignDriverRequest;
import com.yowyob.tiibntick.core.incident.application.query.IncidentRequesterContext;
import com.yowyob.tiibntick.core.incident.port.inbound.IAssignReplacementDriverUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IConfirmHandoverUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IQueryIncidentUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * REST controller for driver handover operations under /api/v1/incidents/{id}/handover.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/handover")
@RequiredArgsConstructor
public class IncidentHandoverController {
    private final IAssignReplacementDriverUseCase assignDriverUseCase;
    private final IConfirmHandoverUseCase confirmHandoverUseCase;
    private final IQueryIncidentUseCase queryIncidentUseCase;

    private IncidentRequesterContext contextOf(TntUserIdentity identity) {
        return new IncidentRequesterContext(
                identity.actorId(), identity.tenantId(), identity.hasPermission("incident", "manage"));
    }

    @PostMapping("/assign-driver")
    public Mono<?> assignDriver(@CurrentUser TntUserIdentity identity,
                                @PathVariable UUID incidentId,
                                @Valid @RequestBody AssignDriverRequest req) {
        return queryIncidentUseCase.getById(incidentId, contextOf(identity))
                .flatMap(existing -> assignDriverUseCase.execute(AssignReplacementDriverCommand.builder()
                        .incidentId(incidentId)
                        .replacementDriverId(req.getReplacementDriverId())
                        .replacementVehicleId(req.getReplacementVehicleId())
                        .replacementAgencyId(req.getReplacementAgencyId())
                        .assignedByActorId(req.getAssignedByActorId())
                        .manualAssignment(req.isManualAssignment())
                        .build()));
    }

    @PostMapping("/confirm")
    public Mono<?> confirm(@CurrentUser TntUserIdentity identity,
                           @PathVariable UUID incidentId,
                           @RequestParam UUID actorId,
                           @RequestParam boolean confirmingAsOriginalDriver) {
        return queryIncidentUseCase.getById(incidentId, contextOf(identity))
                .flatMap(existing -> confirmHandoverUseCase.execute(ConfirmHandoverCommand.builder()
                        .incidentId(incidentId)
                        .actorId(actorId)
                        .confirmingAsOriginalDriver(confirmingAsOriginalDriver)
                        .build()));
    }
}
