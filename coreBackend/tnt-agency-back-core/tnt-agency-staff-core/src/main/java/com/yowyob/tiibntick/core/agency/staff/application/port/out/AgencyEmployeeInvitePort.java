package com.yowyob.tiibntick.core.agency.staff.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Creates (or links) a Kernel user inside the agency organization via Core HRM proxy
 * {@code POST /api/employees/invite}.
 */
public interface AgencyEmployeeInvitePort {

    Mono<InvitedEmployee> invite(InviteCommand command);

    record InviteCommand(
            UUID tenantId,
            UUID kernelOrganizationId,
            UUID kernelAgencyId,
            String firstName,
            String lastName,
            String email,
            String password) {
    }

    record InvitedEmployee(UUID userId, UUID actorId, String email) {
    }
}
