package com.yowyob.tiibntick.core.trust.adapter.out.rest;

import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BlockchainProof;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * REST Adapter — {@code TrustEventRestClientAdapter}.
 *
 * <p>Implements {@link TrustProofQueryPort} by calling the internal REST API
 * of the {@code yow-trust-event} Kernel microservice.
 *
 * <h3>Target Base URL</h3>
 * <p>Configured via {@code tnt.trust.trust-event-base-url} property
 * (default: {@code http://yow-trust-event:8085}).
 *
 * <h3>Endpoints Used</h3>
 * <ul>
 *   <li>{@code GET /kernel/trust/events/verify?txHash=...&expectedHash=...}</li>
 *   <li>{@code GET /kernel/trust/events/entity/{entityType}/{entityId}?tenantId=...}</li>
 * </ul>
 *
 * <h3>Resilience (§15.3 of {@code TNT_CORE_Connexion_Trust_Module.md})</h3>
 * <p>This is the only path genuinely coupled to {@code yow-trust-event}'s own
 * availability (unlike the Kafka write path, which Kafka decouples). Every
 * method is guarded by the {@code trustEventGatewayRead} circuit breaker and
 * time limiter so a down {@code yow-trust-event} degrades to an explicit
 * "unavailable" result instead of leaving a caller (e.g. a controller
 * exposing verification status to an end user) blocked in a timeout loop.
 * A 404 ({@link WebClientResponseException.NotFound}) is treated as a normal
 * "not found" business outcome, not a gateway failure, and is handled inline
 * rather than through the circuit breaker.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Component
public class TrustEventRestClientAdapter implements TrustProofQueryPort {

    private static final Logger log = LoggerFactory.getLogger(TrustEventRestClientAdapter.class);

    private final WebClient webClient;

    public TrustEventRestClientAdapter(
            final WebClient trustEventWebClient) {
        this.webClient = trustEventWebClient;
    }

    /** {@inheritDoc} */
    @Override
    @CircuitBreaker(name = "trustEventGatewayRead", fallbackMethod = "findTxHashFallback")
    @TimeLimiter(name = "trustEventGatewayRead")
    public Mono<String> findTxHashByEntityId(
            final String entityId,
            final String entityType,
            final String tenantId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/kernel/trust/events/entity/{entityType}/{entityId}")
                        .queryParam("tenantId", tenantId)
                        .build(entityType, entityId))
                .retrieve()
                .bodyToFlux(TrustEventSummaryResponse.class)
                .filter(r -> "COMMITTED".equals(r.status()) && r.txHash() != null)
                .map(TrustEventSummaryResponse::txHash)
                .next()
                .doOnNext(tx -> log.debug("Found txHash={} for entity={}/{}", tx, entityType, entityId))
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }

    /** Never blocks the caller: reports "no proof found" rather than propagating the gateway failure. */
    private Mono<String> findTxHashFallback(
            final String entityId, final String entityType, final String tenantId, final Throwable ex) {
        log.warn("Trust Event REST call failed for entity={}/{}: {}", entityType, entityId, ex.getMessage());
        return Mono.empty();
    }

    /** {@inheritDoc} */
    @Override
    @CircuitBreaker(name = "trustEventGatewayRead", fallbackMethod = "verifyProofFallback")
    @TimeLimiter(name = "trustEventGatewayRead")
    public Mono<Boolean> verifyProof(final String txHash, final String expectedHash) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/kernel/trust/events/verify")
                        .queryParam("txHash", txHash)
                        .queryParam("expectedHash", expectedHash)
                        .build())
                .retrieve()
                .bodyToMono(ProofVerificationResponse.class)
                .map(ProofVerificationResponse::valid)
                .doOnNext(valid -> log.debug("Proof verification txHash={} → valid={}", txHash, valid));
    }

    /** Never blocks the caller: reports verification unavailable, not an error. */
    private Mono<Boolean> verifyProofFallback(final String txHash, final String expectedHash, final Throwable ex) {
        log.warn("Proof verification unavailable for txHash={}: {}", txHash, ex.getMessage());
        return Mono.just(false);
    }

    /** {@inheritDoc} */
    @Override
    @CircuitBreaker(name = "trustEventGatewayRead", fallbackMethod = "getAuditHistoryFallback")
    @TimeLimiter(name = "trustEventGatewayRead")
    public Flux<String> getAuditHistory(final String entityId, final String entityType) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/kernel/trust/events/entity/{entityType}/{entityId}")
                        .build(entityType, entityId))
                .retrieve()
                .bodyToFlux(TrustEventSummaryResponse.class)
                .filter(r -> r.txHash() != null)
                .map(TrustEventSummaryResponse::txHash);
    }

    /** Never blocks the caller: reports an empty history rather than propagating the gateway failure. */
    private Flux<String> getAuditHistoryFallback(final String entityId, final String entityType, final Throwable ex) {
        log.warn("Audit history query unavailable for entity={}/{}: {}", entityType, entityId, ex.getMessage());
        return Flux.empty();
    }

    // ── Response records ──────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    @CircuitBreaker(name = "trustEventGatewayRead", fallbackMethod = "getAuditHistoryWithDetailsFallback")
    @TimeLimiter(name = "trustEventGatewayRead")
    public Flux<BlockchainProof> getAuditHistoryWithDetails(
            final String entityId,
            final String entityType,
            final String tenantId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/kernel/trust/events/entity/{entityType}/{entityId}")
                        .queryParam("tenantId", tenantId)
                        .build(entityType, entityId))
                .retrieve()
                .bodyToFlux(TrustEventDetailResponse.class)
                .filter(r -> r.txHash() != null)
                .map(r -> new BlockchainProof(
                        r.id(), r.entityType(), r.entityId(),
                        r.eventType(), r.status(),
                        r.txHash(), r.proofHash(), r.previousProofHash(),
                        r.payload(),
                        r.occurredAt() != null ? parseTimestamp(r.occurredAt()) : null));
    }

    /** Never blocks the caller: reports an empty history rather than propagating the gateway failure. */
    private Flux<BlockchainProof> getAuditHistoryWithDetailsFallback(
            final String entityId, final String entityType, final String tenantId, final Throwable ex) {
        log.warn("Audit history (details) query unavailable for entity={}/{}: {}",
                entityType, entityId, ex.getMessage());
        return Flux.empty();
    }

    private static LocalDateTime parseTimestamp(final String raw) {
        try {
            return LocalDateTime.parse(raw);
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Minimal projection of the Trust Event REST response used for tx hash extraction.
     */
    private record TrustEventSummaryResponse(
            String id,
            String entityType,
            String entityId,
            String status,
            String txHash,
            String occurredAt) {}

    /**
     * Rich projection of the Trust Event REST response including payload and proof hashes.
     */
    private record TrustEventDetailResponse(
            String id,
            String entityType,
            String entityId,
            String eventType,
            String status,
            String txHash,
            String proofHash,
            String previousProofHash,
            String payload,
            String occurredAt) {}

    /**
     * Proof verification response from {@code /kernel/trust/events/verify}.
     */
    private record ProofVerificationResponse(
            String txHash,
            String expectedHash,
            boolean valid,
            String verifiedAt) {}
}
