package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.ParcelPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcParcelRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.ParcelRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Persistence adapter for {@link ParcelRepository}. */
@Component
@RequiredArgsConstructor
public class ParcelRepositoryAdapter implements ParcelRepository {

    private final R2dbcParcelRepository parcelRepo;
    private final ParcelWriter parcelWriter;

    @Override
    public Mono<Parcel> save(Parcel parcel) {
        return parcelWriter.saveIfPending(parcel);
    }

    @Override
    public Mono<Parcel> findByTrackingCode(String trackingCode) {
        return parcelRepo.findByTrackingCode(trackingCode).map(ParcelPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Parcel> findById(UUID parcelId) {
        return parcelRepo.findById(parcelId).map(ParcelPersistenceMapper::toDomain);
    }
}
