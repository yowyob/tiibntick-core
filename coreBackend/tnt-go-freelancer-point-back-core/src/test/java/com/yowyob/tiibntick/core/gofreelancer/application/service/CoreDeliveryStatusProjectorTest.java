package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Core mission events are projected onto the gofp delivery")
class CoreDeliveryStatusProjectorTest {

    @Mock private DeliveryRepository repository;
    private CoreDeliveryStatusProjector projector;
    private final UUID id = UUID.randomUUID();
    private final Instant at = Instant.parse("2026-11-11T12:00:00Z");

    @BeforeEach
    void setUp() {
        projector = new CoreDeliveryStatusProjector(repository);
    }

    private Delivery row(DeliveryStatus status) {
        Delivery d = new Delivery();
        d.setId(id);
        d.setStatus(status);
        when(repository.findById(id)).thenReturn(Mono.just(d));
        org.mockito.Mockito.lenient().when(repository.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        return d;
    }

    @Test
    @DisplayName("an incident paused the mission in the core: the gofp row follows")
    void pausedByIncident_isProjected() {
        Delivery d = row(DeliveryStatus.IN_TRANSIT);
        StepVerifier.create(projector.project(id, "PAUSED_BY_INCIDENT", at)).expectNext(true).verifyComplete();
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PAUSED_BY_INCIDENT);
        verify(repository).save(d);
    }

    @Test
    @DisplayName("DELIVERED stamps the delivery instant")
    void delivered_stampsTheInstant() {
        Delivery d = row(DeliveryStatus.IN_TRANSIT);
        StepVerifier.create(projector.project(id, "DELIVERED", at)).expectNext(true).verifyComplete();
        assertThat(d.getActualDeliveryTime()).isEqualTo(at);
    }

    @Test
    @DisplayName("replaying the event of a status the row already has writes nothing")
    void replay_isIdempotent() {
        row(DeliveryStatus.IN_TRANSIT);
        StepVerifier.create(projector.project(id, "IN_TRANSIT", at)).expectNext(false).verifyComplete();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a late event cannot resurrect a terminal delivery")
    void terminal_isFinal() {
        Delivery d = row(DeliveryStatus.DELIVERED);
        StepVerifier.create(projector.project(id, "IN_TRANSIT", at)).expectNext(false).verifyComplete();
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a delivery unknown to gofp (agency delivery) is ignored")
    void unknownDelivery_isIgnored() {
        when(repository.findById(id)).thenReturn(Mono.empty());
        StepVerifier.create(projector.project(id, "IN_TRANSIT", at)).expectNext(false).verifyComplete();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a core status gofp does not model is ignored instead of corrupting the row")
    void unknownStatus_isIgnored() {
        Delivery d = row(DeliveryStatus.IN_TRANSIT);
        StepVerifier.create(projector.project(id, "SOMETHING_NEW", at)).expectNext(false).verifyComplete();
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }
}
