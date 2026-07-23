package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DisputeCommandService}.
 *
 * <p>All outbound ports are mocked. Tests verify orchestration logic:
 * correct port calls, state after command execution, error propagation.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeCommandService")
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class DisputeCommandServiceTest {

    @Mock private IDisputeRepository repository;
    @Mock private IDisputeEventPublisher eventPublisher;
    @Mock private IDisputeNotificationPort notificationPort;
    @Mock private IDeliveryStatusPort deliveryStatusPort;
    @Mock private IBillingCompensationPort billingCompensationPort;
    @Mock private IBlockchainProofPort blockchainProofPort;
    @Mock private IDisputeReferenceGenerator referenceGenerator;

    private DisputeCommandService service;

    @BeforeEach
    void setUp() {
        service = new DisputeCommandService(
                repository, eventPublisher, notificationPort,
                deliveryStatusPort, billingCompensationPort, blockchainProofPort,
                referenceGenerator);
        when(referenceGenerator.nextReference())
                .thenReturn(Mono.just(DisputeReference.forSequence(1)));
    }

    // =========================================================================
    // openDispute
    // =========================================================================

    @Test
    @DisplayName("openDispute() - should save dispute and publish events")
    void shouldOpenDispute() {
        OpenDisputeCommand cmd = buildOpenCmd();
        Dispute saved = Dispute.open(cmd, DisputeReference.forSequence(1));

        when(repository.existsActiveDisputeForPackage(anyString(), anyString()))
                .thenReturn(Mono.just(false));
        when(repository.save(any(Dispute.class))).thenReturn(Mono.just(saved));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());
        when(notificationPort.notifyDisputeOpened(any())).thenReturn(Mono.empty());
        when(deliveryStatusPort.markPackageAsDisputed(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.openDispute(cmd))
                .assertNext(dispute -> {
                    assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.OPEN);
                    assertThat(dispute.getClaimantId()).isEqualTo("client-abc");
                })
                .verifyComplete();

        verify(repository).save(any());
        verify(eventPublisher).publishAll(any());
        verify(notificationPort).notifyDisputeOpened(any());
        verify(deliveryStatusPort).markPackageAsDisputed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("openDispute() - should fail if active dispute exists for package")
    void shouldFailWhenDuplicateExists() {
        OpenDisputeCommand cmd = buildOpenCmd();
        when(repository.existsActiveDisputeForPackage(anyString(), anyString()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.openDispute(cmd))
                .expectError()
                .verify();

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishAll(any());
    }

    @Test
    @DisplayName("openDispute() - should not mark package as disputed when packageId is null")
    void shouldNotMarkPackageWhenNoPackageId() {
        OpenDisputeCommand cmd = new OpenDisputeCommand(
                "tenant-01", "client-abc", ClaimantType.CLIENT,
                "freelancer-xyz", RespondentType.FREELANCER,
                DisputeCause.PAYMENT_DISPUTE, DisputeCategory.MISSION_GO,
                DisputePriority.LOW, "mission-001", null, null,
                "Payment dispute with no package",
                null, null, false);

        Dispute saved = Dispute.open(cmd, DisputeReference.forSequence(1));
        when(repository.existsActiveDisputeForPackage(isNull(), anyString()))
                .thenReturn(Mono.just(false));
        when(repository.save(any())).thenReturn(Mono.just(saved));
        when(eventPublisher.publishAll(any())).thenReturn(Mono.empty());
        when(notificationPort.notifyDisputeOpened(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.openDispute(cmd))
                .assertNext(d -> assertThat(d.getPackageId()).isNull())
                .verifyComplete();

        verify(deliveryStatusPort, never()).markPackageAsDisputed(any(), any(), any());
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private OpenDisputeCommand buildOpenCmd() {
        return new OpenDisputeCommand(
                "tenant-01",
                "client-abc",
                ClaimantType.CLIENT,
                "freelancer-xyz",
                RespondentType.FREELANCER,
                DisputeCause.PACKAGE_DAMAGED,
                DisputeCategory.MISSION_GO,
                DisputePriority.HIGH,
                "mission-001",
                "pkg-001",
                "TKG-001",
                "Package was damaged on delivery",
                null,
                null,
                false);
    }
}
