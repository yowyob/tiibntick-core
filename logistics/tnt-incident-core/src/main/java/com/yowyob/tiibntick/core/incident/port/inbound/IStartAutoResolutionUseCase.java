package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Inbound port: start the automatic resolution process for a triaged incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IStartAutoResolutionUseCase {
    Mono<Incident> execute(UUID incidentId);
}
