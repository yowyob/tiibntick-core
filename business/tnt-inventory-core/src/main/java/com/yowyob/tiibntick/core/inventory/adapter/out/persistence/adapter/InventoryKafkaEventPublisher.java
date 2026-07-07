package com.yowyob.tiibntick.core.inventory.adapter.out.persistence.adapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.inventory.application.port.out.InventoryEventPublisherPort;
import com.yowyob.tiibntick.core.inventory.domain.event.PackageDepositedEvent;
import com.yowyob.tiibntick.core.inventory.domain.event.PackagePickedUpEvent;
import com.yowyob.tiibntick.core.inventory.domain.event.StockLowEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;


@Component
public class InventoryKafkaEventPublisher implements InventoryEventPublisherPort {
    private static final String STOCK_LOW_TOPIC = "tnt.inventory.stock.low";
    private static final String PKG_DEPOSITED_TOPIC = "tnt.inventory.hub.package.deposited";
    private static final String PKG_PICKEDUP_TOPIC = "tnt.inventory.hub.package.pickedup";
    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;
    public InventoryKafkaEventPublisher(KafkaSender<String, String> kafkaSender,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.kafkaSender = kafkaSender; this.objectMapper = objectMapper;
    }
    @Override public Mono<Void> publishStockLow(StockLowEvent event) { return send(STOCK_LOW_TOPIC, event.productId().toString(), event); }
    @Override public Mono<Void> publishPackageDeposited(PackageDepositedEvent event) { return send(PKG_DEPOSITED_TOPIC, event.trackingCode(), event); }
    @Override public Mono<Void> publishPackagePickedUp(PackagePickedUpEvent event) { return send(PKG_PICKEDUP_TOPIC, event.trackingCode(), event); }
    private Mono<Void> send(String topic, String key, Object payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .flatMap(json -> {
                    SenderRecord<String, String, String> record = SenderRecord.create(new ProducerRecord<>(topic, key, json), key);
                    return kafkaSender.send(Mono.just(record)).then();
                });
    }
}
