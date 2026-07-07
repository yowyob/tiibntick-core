package com.yowyob.tiibntick.core.incident.adapter.web;
import com.yowyob.tiibntick.core.incident.adapter.web.dto.IncidentResponse;
import com.yowyob.tiibntick.core.incident.adapter.web.mapper.IncidentWebMapper;
import com.yowyob.tiibntick.core.incident.application.command.StartAgencyHandlingCommand;
import com.yowyob.tiibntick.core.incident.port.inbound.IStartAgencyHandlingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * REST controller for agency-level incident handling under /api/v1/incidents/{id}/agency.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/agency")
@RequiredArgsConstructor
public class IncidentAgencyController {
    private final IStartAgencyHandlingUseCase startAgencyHandlingUseCase;
    private final IncidentWebMapper webMapper;

    @PostMapping("/handle")
    public Mono<IncidentResponse> startHandling(@PathVariable UUID incidentId,
                                                 @RequestParam UUID agencyId,
                                                 @RequestParam UUID initiatedByActorId,
                                                 @RequestParam(required = false) UUID assignedToActorId,
                                                 @RequestParam(required = false) String notes) {
        return startAgencyHandlingUseCase.execute(StartAgencyHandlingCommand.builder()
                .incidentId(incidentId).agencyId(agencyId)
                .initiatedByActorId(initiatedByActorId)
                .assignedToActorId(assignedToActorId)
                .notes(notes)
                .build()).map(webMapper::toResponse);
    }
}
