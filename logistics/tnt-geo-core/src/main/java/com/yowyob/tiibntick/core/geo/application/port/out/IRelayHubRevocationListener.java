package com.yowyob.tiibntick.core.geo.application.port.out;

import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import reactor.core.publisher.Mono;

/**
 * Outbound port: told once a hub has been revoked and saved (D-129). Implemented by higher modules (e.g. the
 * Go-Freelancer backend notifies the relay operator and aligns its relay point). A listener failure never
 * cancels the revocation, which is already recorded: it is logged.
 */
public interface IRelayHubRevocationListener {

    Mono<Void> onRevoked(RelayHubRevocation revocation);
}
