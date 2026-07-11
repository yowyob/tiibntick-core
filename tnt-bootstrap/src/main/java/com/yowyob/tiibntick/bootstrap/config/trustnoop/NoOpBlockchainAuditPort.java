package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.incident.port.outbound.IBlockchainAuditPort;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * No-op fallback for {@link IBlockchainAuditPort}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpBlockchainAuditPort implements IBlockchainAuditPort {

    @Override
    public Mono<String> writeIncidentEvent(
            final UUID incidentId, final String chainId, final String eventType, final String payload) {
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> verifyChain(final String chainId) {
        return Mono.just(false);
    }

    @Override
    public Mono<String> getParcelChainTailHash(final UUID parcelId) {
        return Mono.empty();
    }
}
