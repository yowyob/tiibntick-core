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
    private final com.yowyob.tiibntick.core.incident.application.service.IncidentAutoResolutionService autoResolution;

    private IncidentRequesterContext contextOf(TntUserIdentity identity) {
        return new IncidentRequesterContext(
                identity.actorId(), identity.tenantId(), identity.hasPermission("incident", "manage"));
    }

    @PostMapping("/assign-driver")
    public Mono<?> assignDriver(@CurrentUser TntUserIdentity identity,
                                @PathVariable UUID incidentId,
                                @Valid @RequestBody AssignDriverRequest req) {
        IncidentAccess.requireManage(identity);
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
        IncidentAccess.requireSelfUnlessManager(identity, actorId);
        // the replacement courier is not the declarant: the use case checks that actorId is the courier of that side
        return confirmHandoverUseCase.execute(ConfirmHandoverCommand.builder()
                .incidentId(incidentId)
                .actorId(actorId)
                .confirmingAsOriginalDriver(confirmingAsOriginalDriver)
                .build());
    }

    /** I12/I16 (lot L3): the proposed replacement courier accepts the delivery (the first one wins, [D.4]). */
    @PostMapping("/accept")
    public Mono<?> accept(@CurrentUser TntUserIdentity identity, @PathVariable UUID incidentId) {
        IncidentAccess.requireCreate(identity);
        return autoResolution.acceptReplacement(incidentId, identity.actorId());
    }
}
