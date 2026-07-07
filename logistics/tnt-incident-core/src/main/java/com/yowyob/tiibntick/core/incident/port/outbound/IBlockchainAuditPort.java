package com.yowyob.tiibntick.core.incident.port.outbound;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: write and verify blockchain events via tnt-trust.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IBlockchainAuditPort {
    Mono<String> writeIncidentEvent(UUID incidentId, String chainId,
                                    String eventType, String payload);
    Mono<Boolean> verifyChain(String chainId);
    Mono<String> getParcelChainTailHash(UUID parcelId);
}
