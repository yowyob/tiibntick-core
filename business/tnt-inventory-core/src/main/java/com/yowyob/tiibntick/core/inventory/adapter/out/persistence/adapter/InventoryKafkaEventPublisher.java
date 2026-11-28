package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.event.application.port.in.PublishEventUseCase;
import com.yowyob.kernel.event.domain.model.DomainEventEnvelope;
import com.yowyob.tiibntick.core.inventory.application.port.out.InventoryEventPublisherPort;
import com.yowyob.tiibntick.core.inventory.domain.event.StockLowEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Outbox-backed adapter implementing {@link InventoryEventPublisherPort}.
 *
 * <p>Chantier C · Audit n°3 · P5 migration (see
 * {@code docs/audits/remediation/chantier-c-p5-inventory.md}): delegates to
 * {@link PublishEventUseCase} (yow-event-kernel's transactional outbox) instead of
 * sending through reactor-kafka's {@code KafkaSender} directly. Envelopes are
 * persisted in the same DB transaction as the business write (see the
 * {@code @Transactional} boundaries on {@code HubInventoryApplicationService}
 * deposit/pickup and {@code InventoryApplicationService#consumeStock}), and
 * {@code OutboxPollerService} relays them to Kafka asynchronously with retry/DLQ —
 * a business save can no longer succeed while its event is silently lost.
 *
 * <p>The Kafka wire format is unchanged: the payload is still simply
 * {@code objectMapper.writeValueAsString(event)} — the raw event record serialized
 * directly, with no extra wrapper envelope — and the message keys are preserved
 * (productId for stock-low, via an explicit {@code kafkaPartitionKey}), so existing consumers require no change.
 *
 * <p>Lot L8: the hub package topics ({@code tnt.inventory.hub.package.deposited / pickedup}) are gone — a parcel
 * entering or leaving a relay hub is {@code ParcelAtRelayHub} / the next parcel transition on
 * {@code tnt.parcel.events}.
 *
 * @author MANFOUO Braun
 */
@Component
public class InventoryKafkaEventPublisher implements InventoryEventPublisherPort {

    static final String STOCK_LOW_TOPIC = "tnt.inventory.stock.low";

    private static final String AGGREGATE_TYPE_STOCK_ENTRY = "StockEntry";
    private static final String SOLUTION_CODE = "TNT";

    private final PublishEventUseCase publishEventUseCase;
    private final ObjectMapper objectMapper;

    public InventoryKafkaEventPublisher(PublishEventUseCase publishEventUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.publishEventUseCase = publishEventUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publishStockLow(StockLowEvent event) {
        return enqueue(STOCK_LOW_TOPIC, event,
                event.productId().toString(), AGGREGATE_TYPE_STOCK_ENTRY,
                event.productId().toString(), event.tenantId(), event.occurredAt());
    }

    private Mono<Void> enqueue(String topic, Object event, String aggregateId, String aggregateType,
                               String partitionKey, UUID tenantId, Instant occurredAt) {
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
                        .kafkaPartitionKey(partitionKey)
                        .occurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .build())
                .flatMap(publishEventUseCase::publish);
    }
}
