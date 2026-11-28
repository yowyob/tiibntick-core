package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.mapper.ParcelPersistenceMapper;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcParcelRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * D-136 backfill: gives a tracking code to every parcel registered before the code moved to the parcel and not
 * covered by changeset {@code evt-l2-009} (no delivery code, or several distinct delivery codes — none of them is
 * chosen silently: the count is logged, the old codes keep resolving through the delivery rows). Idempotent: only
 * parcels without code are touched; a unique-index clash on a fresh code is retried on the next start.
 */
@Slf4j
@Component
public class ParcelTrackingCodeBackfill {

    private static final int BATCH = 500;

    private final R2dbcParcelRepository parcelRepo;
    private final ParcelWriter parcelWriter;
    private final boolean enabled;

    public ParcelTrackingCodeBackfill(R2dbcParcelRepository parcelRepo, ParcelWriter parcelWriter,
                                      @Value("${tnt.delivery.tracking-code-backfill.enabled:true}") boolean enabled) {
        this.parcelRepo = parcelRepo;
        this.parcelWriter = parcelWriter;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            return;
        }
        run().subscribe(
                count -> { },
                e -> log.warn("Parcel tracking-code backfill failed (retried at next start): {}", e.getMessage()));
    }

    /** @return the number of parcels that received a code */
    public Mono<Long> run() {
        return parcelRepo.countParcelsWithSeveralLegacyCodes()
                .doOnNext(multi -> {
                    if (multi > 0) {
                        log.warn("D-136: {} parcel(s) carried several distinct delivery codes; they get a new parcel "
                                + "code, the old delivery codes still resolve to them", multi);
                    }
                })
                .then(backfillBatch(0L));
    }

    private Mono<Long> backfillBatch(long done) {
        return parcelRepo.findWithoutTrackingCode(BATCH)
                .map(ParcelPersistenceMapper::toDomain)
                .filter(Parcel::ensureTrackingCode)
                .concatMap(parcelWriter::saveIfPending)
                .count()
                .flatMap(n -> {
                    long total = done + n;
                    if (n < BATCH) {
                        if (total > 0) {
                            log.info("D-136: {} parcel(s) received a tracking code", total);
                        }
                        return Mono.just(total);
                    }
                    return backfillBatch(total);
                });
    }
}
