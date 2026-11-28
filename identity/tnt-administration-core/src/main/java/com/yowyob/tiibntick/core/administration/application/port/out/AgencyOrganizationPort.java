package com.yowyob.tiibntick.core.administration.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/** Tells whether a Kernel organization is a TiiBnTick Agency (only agencies may override the thresholds). */
public interface AgencyOrganizationPort {

    Mono<Boolean> isAgency(UUID organizationId);
}
