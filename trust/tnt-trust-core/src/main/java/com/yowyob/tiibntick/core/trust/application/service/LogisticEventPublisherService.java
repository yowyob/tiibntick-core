package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustEventPublisherPort;

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
 * <h3>Durability — transactional outbox (Chantier C · Audit n°3 · P5)</h3>
 * <p>{@link TrustEventPublisherPort} is now implemented by an outbox-backed
 * adapter: {@link #publish} persists the event into {@code yow-event-kernel}'s
 * transactional outbox inside the caller's active DB transaction (the chain
 * services' {@code @Transactional} methods), and the outbox poller relays it
 * to Kafka asynchronously with retry/DLQ. This replaces the previous bespoke
 * resilience mechanism (availability guard + {@code trustEventGatewayWrite}
 * circuit breaker + {@code trust_retry_queue}, §15 of the Trust connexion
 * design): the outbox enqueue is a plain DB insert, so a broken Kafka broker
 * can no longer fail — or silently lose — a publish; broker outages are
 * absorbed by the poller's retry policy instead.
 *
 * <p>Error semantics changed accordingly: {@link #publish} now propagates an
 * outbox-persistence failure to the caller, which rolls back the surrounding
 * business transaction — the anchoring record and its trust event succeed or
 * fail together (previously the event was queued best-effort and the business
 * write could survive alone).
 *
 * @author MANFOUO Braun
 * @version 2.0
 */
@Service
public class LogisticEventPublisherService {

    private static final Logger log = LoggerFactory.getLogger(LogisticEventPublisherService.class);

    private final TrustEventPublisherPort publisherPort;
    private final MeterRegistry meterRegistry;

    public LogisticEventPublisherService(
            final TrustEventPublisherPort publisherPort,
            final MeterRegistry meterRegistry) {
        this.publisherPort = publisherPort;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Publishes a single {@link LogisticTrustEvent} through the transactional
     * outbox.
     *
     * @param event the logistic trust event to publish
     * @return a {@link Mono} completing when the event is durably enqueued in
     *         the outbox, or erroring (rolling back the caller's transaction)
     *         if the outbox write fails
     */
    public Mono<Void> publish(final LogisticTrustEvent event) {
        log.info("Publishing LogisticTrustEvent — correlationId={}, type={}, entity={}/{}",
                event.getCorrelationId(), event.getLogisticEventType(),
                event.getEntityType(), event.getEntityId());

        return publisherPort.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.event.published",
                            "type", event.getLogisticEventType().name()).increment();
                    log.debug("LogisticTrustEvent enqueued to outbox — correlationId={}",
                            event.getCorrelationId());
                })
                .doOnError(e -> {
                    meterRegistry.counter("tnt.trust.event.publish.error",
                            "type", event.getLogisticEventType().name()).increment();
                    log.error("Failed to enqueue LogisticTrustEvent correlationId={}: {}",
                            event.getCorrelationId(), e.getMessage());
                });
    }

    /**
     * Publishes a batch of logistic trust events atomically through the outbox.
     * Useful for anchoring multiple events resulting from a single domain
     * operation (e.g., mission creation triggers both MISSION_CREATED_ON_CHAIN
     * and PACKAGE_CUSTODY_TRANSFERRED events).
     *
     * @param events the list of events to publish in order
     * @return a {@link Mono} completing when all events are durably enqueued
     */
    public Mono<Void> publishAll(final List<LogisticTrustEvent> events) {
        if (events == null || events.isEmpty()) {
            return Mono.empty();
        }
        return publisherPort.publishAll(events)
                .doOnSuccess(v -> {
                    events.forEach(event -> meterRegistry.counter("tnt.trust.event.published",
                            "type", event.getLogisticEventType().name()).increment());
                    log.info("Enqueued {} LogisticTrustEvents to the outbox in batch", events.size());
                });
    }
}
