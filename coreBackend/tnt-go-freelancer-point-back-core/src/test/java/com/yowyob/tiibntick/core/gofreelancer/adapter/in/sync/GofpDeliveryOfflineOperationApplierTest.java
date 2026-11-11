package com.yowyob.tiibntick.core.gofreelancer.adapter.in.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOpId;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Delivery person's offline queue replayed on the real delivery use-cases")
class GofpDeliveryOfflineOperationApplierTest {

    private final DeliveryStatusApplicationService statusService = mock(DeliveryStatusApplicationService.class);
    private final DeliveryRepository repository = mock(DeliveryRepository.class);
    private final DeliveryProofService proofs = mock(DeliveryProofService.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final GofpDeliveryOfflineOperationApplier applier =
            new GofpDeliveryOfflineOperationApplier(statusService, repository, proofs, mapper);

    private final UUID deliveryId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private OfflineOperation op(OfflineOpType type, String payload) {
        return new OfflineOperation(OfflineOpId.generate(), actor.toString(), UUID.randomUUID().toString(), "device-1",
                type, "DELIVERY", deliveryId.toString(), payload, LocalDateTime.now(), 1);
    }

    private Delivery delivery(DeliveryStatus status) {
        Delivery d = new Delivery();
        d.setId(deliveryId);
        d.setStatus(status);
        return d;
    }

    private void owned() {
        when(statusService.assertCallerIsAssignedDeliveryPerson(deliveryId, actor)).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("only the DELIVERY aggregate is claimed")
    void supports() {
        assertThat(applier.supports("DELIVERY")).isTrue();
        assertThat(applier.supports("MISSION")).isFalse();
        assertThat(applier.supports("MARKET_ORDER")).isFalse();
    }

    @Test
    @DisplayName("a pickup typed offline reaches the online status use-case with its OTP")
    void statusGoesThroughTheUseCase() {
        owned();
        when(repository.findById(deliveryId)).thenReturn(Mono.just(delivery(DeliveryStatus.CREATED)));
        when(statusService.updateStatus(any(), any())).thenReturn(Mono.just(delivery(DeliveryStatus.PICKED_UP)));

        StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE,
                        "{\"status\":\"PICKED_UP\",\"confirmationCode\":\"123456\"}")))
                .assertNext(json -> assertThat(json).contains("\"status\":\"PICKED_UP\""))
                .verifyComplete();

        ArgumentCaptor<DeliveryStatusUpdateDTO> dto = ArgumentCaptor.forClass(DeliveryStatusUpdateDTO.class);
        verify(statusService).updateStatus(org.mockito.ArgumentMatchers.eq(deliveryId), dto.capture());
        assertThat(dto.getValue().getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(dto.getValue().getConfirmationCode()).isEqualTo("123456");
    }

    @Test
    @DisplayName("DELIVERY_CONFIRMATION defaults to DELIVERED; HUB_DEPOSIT to AT_RELAY_POINT with its relay point")
    void defaults() {
        owned();
        UUID relay = UUID.randomUUID();
        when(repository.findById(deliveryId)).thenReturn(Mono.just(delivery(DeliveryStatus.IN_TRANSIT)));
        when(statusService.updateStatus(any(), any())).thenReturn(Mono.just(delivery(DeliveryStatus.DELIVERED)));

        StepVerifier.create(applier.apply(op(OfflineOpType.DELIVERY_CONFIRMATION, "{\"confirmationCode\":\"654321\"}")))
                .expectNextCount(1).verifyComplete();
        ArgumentCaptor<DeliveryStatusUpdateDTO> dto = ArgumentCaptor.forClass(DeliveryStatusUpdateDTO.class);
        verify(statusService).updateStatus(org.mockito.ArgumentMatchers.eq(deliveryId), dto.capture());
        assertThat(dto.getValue().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        StepVerifier.create(applier.apply(op(OfflineOpType.HUB_DEPOSIT,
                        "{\"relayPointId\":\"" + relay + "\",\"clientId\":\"" + UUID.randomUUID() + "\",\"storageFee\":150}")))
                .expectNextCount(1).verifyComplete();
        verify(statusService, org.mockito.Mockito.times(2)).updateStatus(org.mockito.ArgumentMatchers.eq(deliveryId), dto.capture());
        assertThat(dto.getValue().getStatus()).isEqualTo(DeliveryStatus.AT_RELAY_POINT);
        assertThat(dto.getValue().getRelayPointId()).isEqualTo(relay);
        assertThat(dto.getValue().getStorageFee()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("somebody else's queue is refused before anything is touched")
    void ownershipRefused() {
        when(statusService.assertCallerIsAssignedDeliveryPerson(deliveryId, actor))
                .thenReturn(Mono.error(new AccessDeniedException("not yours")));
        StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"IN_TRANSIT\"}")))
                .expectError(AccessDeniedException.class).verify();
        verify(statusService, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("an operation not bound to an actor id is refused")
    void unboundUser() {
        OfflineOperation bad = new OfflineOperation(OfflineOpId.generate(), "not-a-uuid", UUID.randomUUID().toString(),
                null, OfflineOpType.MISSION_STATUS_UPDATE, "DELIVERY", deliveryId.toString(),
                "{\"status\":\"IN_TRANSIT\"}", LocalDateTime.now(), 1);
        StepVerifier.create(applier.apply(bad)).expectError(AccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("a replay of a step already reached is a no-op returning the current state")
    void replayIsIdempotent() {
        owned();
        when(repository.findById(deliveryId)).thenReturn(Mono.just(delivery(DeliveryStatus.IN_TRANSIT)));
        StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"IN_TRANSIT\"}")))
                .assertNext(json -> assertThat(json).contains("\"status\":\"IN_TRANSIT\""))
                .verifyComplete();
        verify(statusService, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("a late earlier step arriving after a later one is skipped, not replayed")
    void staleStepSkipped() {
        owned();
        when(repository.findById(deliveryId)).thenReturn(Mono.just(delivery(DeliveryStatus.DELIVERED)));
        StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"PICKED_UP\"}")))
                .assertNext(json -> assertThat(json).contains("\"status\":\"DELIVERED\""))
                .verifyComplete();
        verify(statusService, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("a real contradiction (FAILED after DELIVERED) is sent to the core, which refuses it loudly")
    void contradictionIsNotHidden() {
        owned();
        when(repository.findById(deliveryId)).thenReturn(Mono.just(delivery(DeliveryStatus.DELIVERED)));
        when(statusService.updateStatus(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("Cannot transition delivery from [DELIVERED] to [FAILED]")));
        StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE,
                        "{\"status\":\"FAILED\",\"reason\":\"absent\"}")))
                .expectErrorMessage("Cannot transition delivery from [DELIVERED] to [FAILED]").verify();
    }

    @Test
    @DisplayName("GPS and anomaly operations are journaled for the owner without touching the lifecycle")
    void journalOnly() {
        owned();
        String payload = "{\"lat\":3.87,\"lng\":11.52}";
        StepVerifier.create(applier.apply(op(OfflineOpType.GPS_UPDATE, payload))).expectNext(payload).verifyComplete();
        verify(statusService, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("invalid payloads and unsupported kinds are rejected as validation errors")
    void validation() {
        for (String bad : new String[]{"not json", "[]", "{}", "{\"status\":\"WHATEVER\"}",
                "{\"status\":\"PAUSED_BY_INCIDENT\"}", "{\"status\":\"CREATED\"}"}) {
            StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE, bad)))
                    .expectError(TntValidationException.class).verify();
        }
        StepVerifier.create(applier.apply(op(OfflineOpType.HUB_DEPOSIT, "{}")))
                .expectError(TntValidationException.class).verify();
        StepVerifier.create(applier.apply(op(OfflineOpType.PACKAGE_SCAN, "{}")))
                .expectError(TntValidationException.class).verify();
        StepVerifier.create(applier.apply(op(OfflineOpType.MARKET_COMMAND, "{}")))
                .expectError(TntValidationException.class).verify();
    }

    @Test
    @DisplayName("an unknown delivery is a validation error")
    void unknownDelivery() {
        owned();
        when(repository.findById(deliveryId)).thenReturn(Mono.empty());
        StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE, "{\"status\":\"IN_TRANSIT\"}")))
                .expectError(TntValidationException.class).verify();
    }

    @Test
    @DisplayName("forward ranking: delivered is beyond relay deposit, and off-path states are never 'reached'")
    void ranking() {
        assertThat(GofpDeliveryOfflineOperationApplier.alreadyReached(DeliveryStatus.DELIVERED, DeliveryStatus.AT_RELAY_POINT)).isTrue();
        assertThat(GofpDeliveryOfflineOperationApplier.alreadyReached(DeliveryStatus.AT_RELAY_POINT, DeliveryStatus.DELIVERED)).isFalse();
        assertThat(GofpDeliveryOfflineOperationApplier.alreadyReached(DeliveryStatus.DELIVERED, DeliveryStatus.FAILED)).isFalse();
        assertThat(GofpDeliveryOfflineOperationApplier.alreadyReached(DeliveryStatus.FAILED, DeliveryStatus.FAILED)).isTrue();
        assertThat(GofpDeliveryOfflineOperationApplier.alreadyReached(DeliveryStatus.PAUSED_BY_INCIDENT, DeliveryStatus.IN_TRANSIT)).isFalse();
    }

    @Test
    @DisplayName("proofs captured offline are attached once the status was accepted, under the caller's name")
    void proofsAfterStatus() {
        owned();
        UUID file = UUID.randomUUID();
        when(repository.findById(deliveryId)).thenReturn(Mono.just(delivery(DeliveryStatus.IN_TRANSIT)));
        when(statusService.updateStatus(any(), any())).thenReturn(Mono.just(delivery(DeliveryStatus.DELIVERED)));
        when(proofs.addAll(any(), any(), any())).thenReturn(reactor.core.publisher.Flux.empty());

        StepVerifier.create(applier.apply(op(OfflineOpType.DELIVERY_CONFIRMATION,
                        "{\"confirmationCode\":\"654321\",\"proofs\":[{\"kind\":\"delivery_photo\",\"fileId\":\"" + file
                                + "\",\"note\":\"porte\",\"capturedAt\":1790000000000},"
                                + "{\"kind\":\"SIGNATURE\",\"fileId\":\"" + UUID.randomUUID() + "\",\"capturedAt\":\"2026-11-11T10:00:00Z\"}]}")))
                .expectNextCount(1).verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<DeliveryProofService.NewProof>> sent = ArgumentCaptor.forClass(java.util.List.class);
        verify(proofs).addAll(org.mockito.ArgumentMatchers.eq(deliveryId), org.mockito.ArgumentMatchers.eq(actor), sent.capture());
        assertThat(sent.getValue()).hasSize(2);
        assertThat(sent.getValue().get(0).kind()).isEqualTo(com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof.Kind.DELIVERY_PHOTO);
        assertThat(sent.getValue().get(0).fileId()).isEqualTo(file);
        assertThat(sent.getValue().get(0).capturedAt()).isEqualTo(java.time.Instant.ofEpochMilli(1790000000000L));
        assertThat(sent.getValue().get(1).capturedAt()).isEqualTo(java.time.Instant.parse("2026-11-11T10:00:00Z"));
    }

    @Test
    @DisplayName("a photo reaching the server after its status still gets attached (step already reached)")
    void proofsOnReachedStep() {
        owned();
        when(repository.findById(deliveryId)).thenReturn(Mono.just(delivery(DeliveryStatus.DELIVERED)));
        when(proofs.addAll(any(), any(), any())).thenReturn(reactor.core.publisher.Flux.empty());
        StepVerifier.create(applier.apply(op(OfflineOpType.DELIVERY_CONFIRMATION,
                        "{\"proofs\":[{\"kind\":\"DELIVERY_PHOTO\",\"fileId\":\"" + UUID.randomUUID() + "\"}]}")))
                .expectNextCount(1).verifyComplete();
        verify(statusService, never()).updateStatus(any(), any());
        verify(proofs).addAll(any(), any(), any());
    }

    @Test
    @DisplayName("a refused transition leaves no orphan evidence")
    void noProofWhenRefused() {
        owned();
        when(repository.findById(deliveryId)).thenReturn(Mono.just(delivery(DeliveryStatus.CREATED)));
        when(statusService.updateStatus(any(), any())).thenReturn(Mono.error(new IllegalStateException("refused")));
        StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE,
                        "{\"status\":\"IN_TRANSIT\",\"proofs\":[{\"kind\":\"OTHER\",\"fileId\":\"" + UUID.randomUUID() + "\"}]}")))
                .expectError(IllegalStateException.class).verify();
        verify(proofs, never()).addAll(any(), any(), any());
    }

    @Test
    @DisplayName("malformed proofs are rejected before anything runs")
    void badProofs() {
        for (String bad : new String[]{"{\"status\":\"IN_TRANSIT\",\"proofs\":\"x\"}",
                "{\"status\":\"IN_TRANSIT\",\"proofs\":[{\"kind\":\"SELFIE\",\"fileId\":\"" + UUID.randomUUID() + "\"}]}",
                "{\"status\":\"IN_TRANSIT\",\"proofs\":[{\"kind\":\"OTHER\"}]}",
                "{\"status\":\"IN_TRANSIT\",\"proofs\":[{\"kind\":\"OTHER\",\"fileId\":\"nope\"}]}",
                "{\"status\":\"IN_TRANSIT\",\"proofs\":[{\"kind\":\"OTHER\",\"fileId\":\"" + UUID.randomUUID() + "\",\"capturedAt\":\"hier\"}]}"}) {
            StepVerifier.create(applier.apply(op(OfflineOpType.MISSION_STATUS_UPDATE, bad)))
                    .expectError(TntValidationException.class).verify();
        }
        verify(statusService, never()).updateStatus(any(), any());
    }
}
