package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: archive incident evidence media via tnt-media-core.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IMediaStoragePort {
    Mono<Void> archiveIncidentEvidence(UUID tenantId, UUID incidentId);
}
