package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryProofRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService.NewProof;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Delivery proofs: who may add, who may read, no duplicates")
class DeliveryProofServiceTest {

    private final DeliveryProofRepository repository = mock(DeliveryProofRepository.class);
    private final DeliveryStatusApplicationService status = mock(DeliveryStatusApplicationService.class);
    private final DeliveryProofService service = new DeliveryProofService(repository, status);

    private final UUID delivery = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();
    private final UUID file = UUID.randomUUID();

    private void assigned() {
        when(status.assertCallerIsAssignedDeliveryPerson(delivery, driver)).thenReturn(Mono.empty());
        when(repository.findByDeliveryIdAndFileId(any(), any())).thenReturn(Mono.empty());
        when(repository.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
    }

    @Test
    @DisplayName("the author is the caller and the proof is tied to the delivery")
    void addsAsCaller() {
        assigned();
        Instant taken = Instant.now().minus(Duration.ofHours(3));
        StepVerifier.create(service.add(delivery, driver, new NewProof(Kind.DELIVERY_PHOTO, file, "  Remis au voisin  ", taken)))
                .assertNext(p -> {
                    assertThat(p.deliveryId()).isEqualTo(delivery);
                    assertThat(p.uploadedBy()).isEqualTo(driver);
                    assertThat(p.kind()).isEqualTo("DELIVERY_PHOTO");
                    assertThat(p.note()).isEqualTo("Remis au voisin");
                    assertThat(p.capturedAt()).isEqualTo(taken);   // captured offline hours ago: kept
                }).verifyComplete();
    }

    @Test
    @DisplayName("somebody who is not the assigned delivery person cannot add, and nothing is stored")
    void refused() {
        when(status.assertCallerIsAssignedDeliveryPerson(delivery, driver))
                .thenReturn(Mono.error(new AccessDeniedException("no")));
        StepVerifier.create(service.add(delivery, driver, new NewProof(Kind.SIGNATURE, file, null, null)))
                .expectError(AccessDeniedException.class).verify();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("the same file attached twice (replayed queue, retried upload) returns the first proof")
    void idempotent() {
        when(status.assertCallerIsAssignedDeliveryPerson(delivery, driver)).thenReturn(Mono.empty());
        DeliveryProof existing = DeliveryProof.builder().id(UUID.randomUUID()).deliveryId(delivery).kind("PICKUP_PHOTO")
                .fileId(file).capturedAt(Instant.now()).uploadedBy(driver).build();
        when(repository.findByDeliveryIdAndFileId(delivery, file)).thenReturn(Mono.just(existing));
        StepVerifier.create(service.add(delivery, driver, new NewProof(Kind.PICKUP_PHOTO, file, null, null)))
                .assertNext(p -> assertThat(p.id()).isEqualTo(existing.getId())).verifyComplete();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a device clock in the future is replaced by the server time; a missing one too")
    void clockSkew() {
        assigned();
        ArgumentCaptor<DeliveryProof> saved = ArgumentCaptor.forClass(DeliveryProof.class);
        Instant before = Instant.now();
        service.add(delivery, driver, new NewProof(Kind.OTHER, file, null, Instant.now().plus(Duration.ofDays(2)))).block();
        service.add(delivery, driver, new NewProof(Kind.OTHER, UUID.randomUUID(), null, null)).block();
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(p -> assertThat(p.getCapturedAt()).isBetween(before, Instant.now()));
    }

    @Test
    @DisplayName("several proofs are checked once and kept in order; one bad proof stores none")
    void batch() {
        assigned();
        UUID f2 = UUID.randomUUID();
        StepVerifier.create(service.addAll(delivery, driver, List.of(
                        new NewProof(Kind.DELIVERY_PHOTO, file, null, null), new NewProof(Kind.SIGNATURE, f2, null, null))))
                .expectNextCount(2).verifyComplete();
        verify(status, org.mockito.Mockito.times(1)).assertCallerIsAssignedDeliveryPerson(delivery, driver);

        StepVerifier.create(service.addAll(delivery, driver, List.of(
                        new NewProof(Kind.DELIVERY_PHOTO, UUID.randomUUID(), null, null), new NewProof(null, f2, null, null))))
                .expectError(TntValidationException.class).verify();
        verify(repository, org.mockito.Mockito.times(2)).save(any());  // still only the two from the first batch
    }

    @Test
    @DisplayName("kind and file are required, the note is bounded")
    void validation() {
        for (NewProof bad : List.of(new NewProof(null, file, null, null), new NewProof(Kind.OTHER, null, null, null),
                new NewProof(Kind.OTHER, file, "x".repeat(501), null))) {
            StepVerifier.create(service.add(delivery, driver, bad)).expectError(TntValidationException.class).verify();
        }
    }

    @Test
    @DisplayName("reading goes through the sender-or-delivery-person guard")
    void listIsGuarded() {
        UUID stranger = UUID.randomUUID();
        when(status.assertCallerCanCancelDelivery(delivery, stranger)).thenReturn(Mono.error(new AccessDeniedException("no")));
        StepVerifier.create(service.list(delivery, stranger)).expectError(AccessDeniedException.class).verify();
        verify(repository, never()).findByDeliveryIdOrderByCapturedAtAsc(any());

        when(status.assertCallerCanCancelDelivery(delivery, driver)).thenReturn(Mono.empty());
        when(repository.findByDeliveryIdOrderByCapturedAtAsc(delivery)).thenReturn(Flux.just(
                DeliveryProof.builder().id(UUID.randomUUID()).deliveryId(delivery).kind("PICKUP_PHOTO").fileId(file)
                        .capturedAt(Instant.now()).uploadedBy(driver).build()));
        StepVerifier.create(service.list(delivery, driver)).expectNextCount(1).verifyComplete();
    }
}
