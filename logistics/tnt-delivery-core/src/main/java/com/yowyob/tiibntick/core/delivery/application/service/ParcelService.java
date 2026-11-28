package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.ParcelUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RegisterParcelCommand;
import com.yowyob.tiibntick.core.delivery.application.port.out.ParcelRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Registers and reads parcels.
 *
 * <p>A parcel is a technical sub-resource of the announcement/delivery flow that owns it: it carries
 * no tenant and no ownership of its own, so no permission is attached here; the caller's own use case
 * (publishing a request, creating a delivery) is the one that authorises the operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelService implements ParcelUseCase {

    private final ParcelRepository parcelRepository;
    private final com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher eventPublisher;

    /**
     * Registers a parcel (row P1) and always publishes {@code ParcelCreated} on {@code tnt.parcel.events}: a parcel
     * registered without tenant belongs to the single system tenant (D-150).
     */
    @Override
    public Mono<Parcel> registerParcel(RegisterParcelCommand command) {
        Parcel parcel = Parcel.create(command.specification(), command.tenantId(), command.senderId(),
                command.senderId() != null
                        ? com.yowyob.tiibntick.common.kafka.TntEventActor.of(
                                com.yowyob.tiibntick.common.kafka.TntEventActor.Type.SENDER, command.senderId())
                        : com.yowyob.tiibntick.common.kafka.TntEventActor.system());
        if (command.photoUrl() != null && !command.photoUrl().isBlank()) {
            parcel.attachPhoto(command.photoUrl());
        }
        java.util.List<com.yowyob.tiibntick.core.delivery.domain.event.DeliveryDomainEvent> events =
                com.yowyob.tiibntick.core.delivery.domain.event.EventSource.drain(parcel);
        return parcelRepository.save(parcel)
                .flatMap(saved -> events.isEmpty()
                        ? Mono.just(saved)
                        : eventPublisher.publishAll(events).thenReturn(saved))
                .doOnSuccess(p -> log.info("Parcel {} registered", p.getId()));
    }

    @Override
    public Mono<Parcel> findParcelById(UUID parcelId) {
        return parcelId == null ? Mono.empty() : parcelRepository.findById(parcelId);
    }
}
