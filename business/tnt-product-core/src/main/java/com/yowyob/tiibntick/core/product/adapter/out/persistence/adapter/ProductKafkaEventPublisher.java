package com.yowyob.tiibntick.core.product.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.product.application.port.out.ProductEventPublisherPort;
import com.yowyob.tiibntick.core.product.domain.event.ProductCreatedEvent;
import com.yowyob.tiibntick.core.product.domain.event.ServiceOfferPublishedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link ProductEventPublisherPort}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending through reactor-kafka's {@code KafkaSender} directly. Envelopes are
 * persisted in the same DB transaction as the business write (see the
 * {@code @Transactional} boundaries on {@code ProductApplicationService#createProduct}
 * and {@code ServiceOfferApplicationService#publishToMarket}), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ —
 * a business save can no longer succeed while its event is silently lost.
 *
 * <p>The Kafka wire format is unchanged: the payload is still simply
 * {@code objectMapper.writeValueAsString(event)} — the raw event record serialized
 * directly, with no extra wrapper envelope — and the message keys are preserved
 * (productId / offerId, the envelope's default partition key), so existing
 * consumers require no change.
 *
 * @author MANFOUO Braun
 */
@Component
public class ProductKafkaEventPublisher implements ProductEventPublisherPort {

    static final String PRODUCT_CREATED_TOPIC = "tnt.product.created";
    static final String OFFER_PUBLISHED_TOPIC = "tnt.product.offer.published";

    private static final String AGGREGATE_TYPE_PRODUCT = "Product";
    private static final String AGGREGATE_TYPE_SERVICE_OFFER = "ServiceOffer";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public ProductKafkaEventPublisher(PublishEventUseCase publishEventUseCase,
                                      @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishProductCreated(ProductCreatedEvent event) {
        return enqueue(PRODUCT_CREATED_TOPIC, event,
                event.productId().toString(), AGGREGATE_TYPE_PRODUCT,
                event.tenantId(), event.occurredAt());
    }

    @Override
    public Mono<Void> publishServiceOfferPublished(ServiceOfferPublishedEvent event) {
        return enqueue(OFFER_PUBLISHED_TOPIC, event,
                event.offerId().toString(), AGGREGATE_TYPE_SERVICE_OFFER,
                event.tenantId(), event.occurredAt());
    }

    private Mono<Void> enqueue(String topic, Object event, String aggregateId, String aggregateType,
                               UUID tenantId, Instant occurredAt) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .map(payload -> DomainEventEnvelope.wrap()
                        .correlationId(UUID.randomUUID().toString())
                        .eventType(event.getClass().getSimpleName())
                        .aggregateId(aggregateId)
                        .aggregateType(aggregateType)
                        .tenantId(tenantId.toString())
                        .solutionCode(SOLUTION_CODE)
                        .payload(payload)
                        .kafkaTopic(topic)
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish);
    }
}
