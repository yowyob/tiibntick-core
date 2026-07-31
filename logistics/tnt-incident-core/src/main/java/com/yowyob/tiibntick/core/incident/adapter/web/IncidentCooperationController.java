package com.yowyob.tiibntick.core.incident.adapter.web;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.incident.adapter.web.dto.CooperationRequest;
import com.yowyob.tiibntick.core.incident.application.command.*;
import com.yowyob.tiibntick.core.incident.application.query.IncidentRequesterContext;
import com.yowyob.tiibntick.core.incident.port.inbound.IInterAgencyCooperationUseCase;
import com.yowyob.tiibntick.core.incident.port.inbound.IQueryIncidentUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * REST controller for inter-agency cooperation under /api/v1/incidents/{id}/cooperation.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/cooperation")
@RequiredArgsConstructor
public class IncidentCooperationController {
    private final IInterAgencyCooperationUseCase cooperationUseCase;
    private final IQueryIncidentUseCase queryIncidentUseCase;

    private IncidentRequesterContext contextOf(TntUserIdentity identity) {
        return new IncidentRequesterContext(
                identity.actorId(), identity.tenantId(), identity.hasPermission("incident", "manage"));
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<?> request(@CurrentUser TntUserIdentity identity,
                           @PathVariable UUID incidentId,
                           @RequestParam UUID requestingAgencyId,
                           @Valid @RequestBody CooperationRequest req) {
        return queryIncidentUseCase.getById(incidentId, contextOf(identity))
                .flatMap(existing -> cooperationUseCase.request(RequestCooperationCommand.builder()
                        .incidentId(incidentId).requestingAgencyId(requestingAgencyId)
                        .respondingAgencyId(req.getRespondingAgencyId())
                        .cooperationType(req.getCooperationType())
                        .details(req.getDetails())
                        .requestedByActorId(req.getRequestedByActorId())
                        .build()));
    }

    @PostMapping("/{cooperationId}/accept")
    public Mono<?> accept(@CurrentUser TntUserIdentity identity,
                          @PathVariable UUID incidentId,
                          @PathVariable UUID cooperationId,
                          @RequestParam UUID respondingAgencyId,
                          @RequestParam UUID respondedByActorId,
                          @RequestParam(required = false) String responseDetails) {
        return queryIncidentUseCase.getById(incidentId, contextOf(identity))
                .flatMap(existing -> cooperationUseCase.accept(RespondToCooperationCommand.builder()
                        .cooperationId(cooperationId).respondingAgencyId(respondingAgencyId)
                        .respondedByActorId(respondedByActorId).responseDetails(responseDetails)
                        .build()));
    }

    @PostMapping("/{cooperationId}/reject")
    public Mono<?> reject(@CurrentUser TntUserIdentity identity,
                          @PathVariable UUID incidentId,
                          @PathVariable UUID cooperationId,
                          @RequestParam UUID respondingAgencyId,
                          @RequestParam UUID respondedByActorId,
                          @RequestParam String rejectionReason) {
        return queryIncidentUseCase.getById(incidentId, contextOf(identity))
                .flatMap(existing -> cooperationUseCase.reject(RespondToCooperationCommand.builder()
                        .cooperationId(cooperationId).respondingAgencyId(respondingAgencyId)
                        .respondedByActorId(respondedByActorId).rejectionReason(rejectionReason)
                        .build()));
    }

    @PostMapping("/{cooperationId}/complete")
    public Mono<?> complete(@CurrentUser TntUserIdentity identity,
                            @PathVariable UUID incidentId,
                            @PathVariable UUID cooperationId,
                            @RequestParam UUID completedByActorId) {
        return queryIncidentUseCase.getById(incidentId, contextOf(identity))
                .flatMap(existing -> cooperationUseCase.complete(RecordCooperationCompletionCommand.builder()
                        .cooperationId(cooperationId).completedByActorId(completedByActorId)
                        .build()));
    }
}
