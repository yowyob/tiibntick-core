package com.yowyob.tiibntick.core.trust.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.adapter.out.health.TrustAvailabilityGuard;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.domain.service.TrustEventEnvelopeMapper;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustEventPublisherPort;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustRetryQueueRepository;

import java.util.List;

/**
 * Application Service — {@code LogisticEventPublisherService}.
 *
 * <p>Translates TiiBnTick logistic domain events into the Kernel's
 * {@code TrustEventKafkaMessage} format and publishes them to the
 * {@code yow.trust.events} Kafka topic via {@link TrustEventPublisherPort}.
 *
 * <p>This is the central "bridge" between TiiBnTick's business domain and
 * the Yowyob Kernel's blockchain infrastructure. It is called by:
 * <ul>
 *   <li>{@link DeliveryProofChainService} — after a delivery proof is created</li>
 *   <li>{@link DIDManagerService} — after a DID is issued or revoked</li>
 *   <li>{@link PolChainService} — after a PoL is verified</li>
 *   <li>{@link BadgeChainService} — after a badge is awarded</li>
 *   <li>{@link DaoRuleChainService} — after a DAO rule is activated</li>
 * </ul>
 *
 * <p>Direct callers outside {@code tnt-trust}: {@code tnt-delivery-core},
 * {@code tnt-billing-wallet} (via Spring injection of the use case interfaces).
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class LogisticEventPublisherService {

    private static final Logger log = LoggerFactory.getLogger(LogisticEventPublisherService.class);

    private final TrustEventPublisherPort publisherPort;
    private final MeterRegistry meterRegistry;
    private final TrustAvailabilityGuard guard;
    private final TrustRetryQueueRepository retryQueue;
    private final ObjectMapper objectMapper;

    public LogisticEventPublisherService(
            final TrustEventPublisherPort publisherPort,
            final MeterRegistry meterRegistry,
            final TrustAvailabilityGuard guard,
            final TrustRetryQueueRepository retryQueue,
            final ObjectMapper objectMapper) {
        this.publisherPort = publisherPort;
        this.meterRegistry = meterRegistry;
        this.guard = guard;
        this.retryQueue = retryQueue;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a single {@link LogisticTrustEvent} to the Kernel trust topic.
     *
     * <p>Resilience (§15 of {@code TNT_CORE_Connexion_Trust_Module.md}): the
     * {@code trustEventGatewayWrite} circuit breaker/time-limiter/retry protect
     * against a broken Kafka broker (not a down {@code yow-trust-event} — Kafka
     * decouples that, see the design doc's §15.3 table). On the fast path, when
     * {@link TrustAvailabilityGuard} already reports the gateway unavailable,
     * the call is skipped entirely and the event goes straight to
     * {@code trust_retry_queue}, avoiding needless circuit-breaker trips.
     * {@link #publishFallback} guarantees this method's {@link Mono} NEVER
     * errors out to the caller ({@code tnt-delivery-core}, {@code tnt-incident-core},
     * etc.) — either the event reaches Kafka, or it is queued for later retry.
     *
     * @param event the logistic trust event to publish
     * @return a {@link Mono} completing when the message is sent, or queued for retry
     */
    @CircuitBreaker(name = "trustEventGatewayWrite", fallbackMethod = "publishFallback")
    @TimeLimiter(name = "trustEventGatewayWrite")
    @Retry(name = "trustEventGatewayWrite")
    public Mono<Void> publish(final LogisticTrustEvent event) {
        log.info("Publishing LogisticTrustEvent — correlationId={}, type={}, entity={}/{}",
                event.getCorrelationId(), event.getLogisticEventType(),
                event.getEntityType(), event.getEntityId());

        if (!guard.isAvailable()) {
            return enqueueForRetry(event, "gateway known unavailable");
        }

        return publisherPort.publish(event)
                .doOnSuccess(v -> {
                    guard.markAvailable();
                    meterRegistry.counter("tnt.trust.event.published",
                            "type", event.getLogisticEventType().name()).increment();
                    log.debug("LogisticTrustEvent published — correlationId={}",
                            event.getCorrelationId());
                })
                .doOnError(e -> {
                    meterRegistry.counter("tnt.trust.event.publish.error",
                            "type", event.getLogisticEventType().name()).increment();
                    log.error("Failed to publish LogisticTrustEvent correlationId={}: {}",
                            event.getCorrelationId(), e.getMessage());
                });
    }

    /**
     * Fallback invoked by Resilience4j on timeout, exception, or open circuit.
     * NEVER propagates the failure to the caller — the event is queued for retry instead.
     */
    private Mono<Void> publishFallback(final LogisticTrustEvent event, final Throwable ex) {
        log.warn("tnt-trust-core: publish failed ({}), queued for retry — correlationId={}",
                ex.getClass().getSimpleName(), event.getCorrelationId());
        guard.markUnavailable();
        return enqueueForRetry(event, ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    private Mono<Void> enqueueForRetry(final LogisticTrustEvent event, final String reason) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(TrustEventEnvelopeMapper.toEnvelope(event));
        } catch (final Exception e) {
            log.error("Failed to serialize event for retry queue correlationId={}: {}",
                    event.getCorrelationId(), e.getMessage());
            return Mono.empty(); // best-effort resilience infra — never propagate
        }
        return retryQueue.enqueue(event.getEntityId(), json, event.getLogisticEventType().name(), reason)
                .doOnSuccess(v -> meterRegistry.counter("tnt.trust.event.retry.enqueued",
                        "type", event.getLogisticEventType().name()).increment());
    }

    /**
     * Publishes a batch of logistic trust events sequentially, each going through
     * the same resilience path (circuit breaker, guard, retry queue) as {@link #publish}.
     * Useful for anchoring multiple events resulting from a single domain operation
     * (e.g., mission creation triggers both MISSION_CREATED_ON_CHAIN and
     * PACKAGE_CUSTODY_TRANSFERRED events).
     *
     * @param events the list of events to publish in order
     * @return a {@link Mono} completing when all events are published or queued for retry
     */
    public Mono<Void> publishAll(final List<LogisticTrustEvent> events) {
        return Flux.fromIterable(events)
                .concatMap(this::publish)
                .then()
                .doOnSuccess(v -> log.info("Published {} LogisticTrustEvents in batch", events.size()));
    }
}
