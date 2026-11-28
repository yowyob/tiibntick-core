package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.ParcelPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcParcelRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The single write path of a parcel (lot evt-l2): a delivery and an announcement share the same {@link Parcel}
 * instance, so the parcel is written only when it has pending changes and the version written is copied back onto the
 * domain object — no optimistic-lock conflict between two adapters saving the same parcel in one transaction, and the
 * parcel events keep the version of the save that persisted them.
 */
@Component
@RequiredArgsConstructor
public class ParcelWriter {

    private final R2dbcParcelRepository parcelRepo;

    /**
     * @return the same parcel, saved if it had pending changes. Lazy: the parcel (version, pending changes) is read
     *         at subscription, so that a save assembled before another save of the same parcel in the same chain
     *         does not write a stale version (optimistic locking failure).
     */
    public Mono<Parcel> saveIfPending(Parcel parcel) {
        return Mono.defer(() -> save(parcel));
    }

    private Mono<Parcel> save(Parcel parcel) {
        if (parcel == null) {
            return Mono.empty();
        }
        if (parcel.getVersion() != null && !parcel.hasPendingChanges()) {
            return Mono.just(parcel);
        }
        var entity = ParcelPersistenceMapper.toEntity(parcel);
        Mono<Boolean> isNew = parcel.getVersion() == null
                ? parcelRepo.existsById(parcel.getId()).map(exists -> !exists)
                : Mono.just(false);
        return isNew.flatMap(newRow -> {
                    entity.setNew(newRow);
                    return parcelRepo.save(entity);
                })
                .map(saved -> {
                    parcel.markPersisted(saved.getVersion());
                    parcel.markClean();
                    return parcel;
                });
    }
}
