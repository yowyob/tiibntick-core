package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.ParcelEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcParcelRepository;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Two saves of the same parcel chained in one flow (announcement then delivery, at the response selection) must not
 * write a stale version: the save reads the parcel when it runs, not when it is assembled.
 */
class ParcelWriterLazinessTest {

    @Test
    @DisplayName("saveIfPending reads the parcel version at subscription, not at assembly")
    void readsTheVersionWhenItRuns() {
        R2dbcParcelRepository repo = mock(R2dbcParcelRepository.class);
        java.util.concurrent.atomic.AtomicLong written = new java.util.concurrent.atomic.AtomicLong(-1);
        when(repo.save(any())).thenAnswer(inv -> {
            ParcelEntity e = inv.getArgument(0);
            written.set(e.getVersion());
            e.setVersion(e.getVersion() + 1);
            return Mono.just(e);
        });
        ParcelWriter writer = new ParcelWriter(repo);
        Parcel parcel = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"));
        parcel.markPersisted(1L);

        Mono<Parcel> second = writer.saveIfPending(parcel); // assembled while the parcel is at version 1
        verifyNoInteractions(repo);

        parcel.markPersisted(2L); // a save chained before it ran meanwhile
        second.block();

        verify(repo).save(any());
        assertThat(written.get()).as("written from version 2, not the stale 1").isEqualTo(2L);
        assertThat(parcel.getVersion()).isEqualTo(3L);
    }
}
