package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.command.WithdrawDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeCompensationException;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static com.yowyob.tiibntick.core.dispute.support.DisputeFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lot L4: the service applies the parcel effect of each row in the dispute's transaction (P12 at LT1, P13–P17 at
 * LT8/LT9/LT10/LT11/LT12), pays the compensation synchronously and keeps the dispute pending when the fund is not there.
 */
class DisputeLifecycleL4ServiceTest {

    private final IDisputeRepository repository = mock(IDisputeRepository.class);
    private final IDisputeParcelPort parcels = mock(IDisputeParcelPort.class);
    private final IDisputeCompensationPort compensations = mock(IDisputeCompensationPort.class);
    private final IDisputeReferenceGenerator references = mock(IDisputeReferenceGenerator.class);
    private DisputeCommandService service;

    private final String parcelId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service = new DisputeCommandService(repository,
                mock(IDisputeNotificationPort.class, i -> Mono.empty()), parcels, compensations,
                mock(IBlockchainProofPort.class), references);
        when(references.nextReference()).thenReturn(Mono.just(DisputeReference.forSequence(3)));
        when(repository.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(repository.existsActiveDisputeForPackage(any(), any())).thenReturn(Mono.just(false));
        when(repository.findByIncidentId(any(), any())).thenReturn(Mono.empty());
        when(parcels.openDispute(any(), any(), any(), anyBoolean(), any())).thenReturn(Mono.empty());
        when(parcels.applyOutcome(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    private Dispute stored(Dispute d) {
        when(repository.findByIdAndTenantId(eq(d.getId()), any())).thenReturn(Mono.just(d));
        return d;
    }

    /** A dispute already on the core parcel, in mediation. */
    private Dispute mediationOnParcel() {
        Dispute d = Dispute.open(open(DisputeCause.PACKAGE_LOST, parcelId, null), DisputeReference.forSequence(1));
        d.assignMediator(MEDIATOR);
        d.startMediation();
        d.clearPendingTransitions();
        return stored(d);
    }

    @Test
    @DisplayName("LT1: the parcel resolved from the delivery goes UNDER_DISPUTE; a delay does not freeze (QO-7)")
    void openingPutsTheParcelUnderDispute() {
        String delivery = UUID.randomUUID().toString();
        when(parcels.resolveParcel(TENANT, null, delivery)).thenReturn(Mono.just(parcelId));

        Dispute d = service.openDispute(open(DisputeCause.DELIVERY_DELAYED, null, delivery)).block();

        assertThat(d.getPackageId()).as("linked to the core parcel").isEqualTo(parcelId);
        verify(parcels).openDispute(eq(TENANT), eq(parcelId), eq(d.getId().getValue()), eq(false), any());
        verify(repository).existsActiveDisputeForPackage(parcelId, TENANT);
        assertThat(DisputeCommandService.freezesDelivery(DisputeCause.PACKAGE_DAMAGED)).isTrue();
    }

    @Test
    @DisplayName("LT1: a parcel refusal (window over, already disputed) fails the opening, nothing is saved")
    void parcelRefusalFailsTheOpening() {
        when(parcels.resolveParcel(any(), any(), any())).thenReturn(Mono.just(parcelId));
        when(parcels.openDispute(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Mono.error(new DisputeStateException("the dispute filing window is over")));

        StepVerifier.create(service.openDispute(open(DisputeCause.PACKAGE_DAMAGED, parcelId, null)))
                .expectError(DisputeStateException.class).verify();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a redelivered incident escalation does not open a second dispute")
    void incidentOpeningIsIdempotent() {
        Dispute existing = stored(Dispute.open(open(DisputeCause.PACKAGE_LOST, null, null),
                DisputeReference.forSequence(9)));
        String incident = UUID.randomUUID().toString();
        when(repository.findByIncidentId(incident, TENANT)).thenReturn(Mono.just(existing));
        OpenDisputeCommand c = open(DisputeCause.PACKAGE_LOST, null, null);
        OpenDisputeCommand fromIncident = new OpenDisputeCommand(c.tenantId(), "SYSTEM", ClaimantType.SYSTEM,
                c.respondentId(), c.respondentType(), c.cause(), c.category(), c.priority(), null, null, null,
                c.description(), null, null, false, incident);
        when(parcels.resolveParcel(any(), any(), any())).thenReturn(Mono.empty());

        assertThat(service.openFromIncident(fromIncident).block()).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("LT8: REDELIVERY_ORDERED applies REDELIVERY to the parcel with the party at fault")
    void rulingWithoutCompensation() {
        Dispute d = mediationOnParcel();
        service.ruleDispute(rule(d, ResolutionType.REDELIVERY_ORDERED, null)).block();

        assertThat(d.getStatus()).isEqualTo(DisputeStatus.CLOSED_RESOLVED);
        verify(parcels).applyOutcome(TENANT, parcelId, d.getId().getValue(), "REDELIVERY",
                "DISPUTE_REDELIVERY_ORDERED", null, DisputeResponsibleParty.COURIER,
                com.yowyob.tiibntick.common.kafka.TntEventActor.of(
                        com.yowyob.tiibntick.common.kafka.TntEventActor.Type.SUPPORT, UUID.fromString(MEDIATOR)));
        verifyNoInteractions(compensations);
    }

    @Test
    @DisplayName("LT7 then LT9: the fund pays, the parcel is LOST with the amount, in the same call")
    void compensationPaid() {
        Dispute d = mediationOnParcel();
        when(compensations.pay(TENANT, d.getId().getValue(), CLAIMANT, new BigDecimal("15000"), "XAF"))
                .thenReturn(Mono.just("KRN-PAY-7"));

        service.ruleDispute(rule(d, ResolutionType.TOTAL_LOSS_COMPENSATED, new BigDecimal("15000"))).block();

        assertThat(d.getStatus()).isEqualTo(DisputeStatus.COMPENSATED);
        assertThat(d.getCompensation().getPaymentReference()).isEqualTo("KRN-PAY-7");
        verify(parcels).applyOutcome(eq(TENANT), eq(parcelId), eq(d.getId().getValue()), eq("LOST"),
                eq("DISPUTE_TOTAL_LOSS_COMPENSATED"), eq(new BigDecimal("15000")),
                eq(DisputeResponsibleParty.COURIER), any());
        verify(repository, times(2)).save(d);
    }

    @Test
    @DisplayName("LT7 without the fund (503): the dispute stays PENDING_COMPENSATION, the parcel untouched; retry → 503")
    void compensationUnavailable() {
        Dispute d = mediationOnParcel();
        when(compensations.pay(any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(DisputeCompensationException.unavailable()));

        Dispute ruled = service.ruleDispute(rule(d, ResolutionType.DAMAGE_COMPENSATED, new BigDecimal("2000")))
                .block();

        assertThat(ruled.getStatus()).isEqualTo(DisputeStatus.PENDING_COMPENSATION);
        verify(parcels, never()).applyOutcome(any(), any(), any(), any(), any(), any(), any(), any());
        StepVerifier.create(service.payCompensation(d.getId(), TENANT))
                .expectErrorSatisfies(e -> assertThat(((DisputeCompensationException) e).getHttpStatus())
                        .isEqualTo(503))
                .verify();
    }

    @Test
    @DisplayName("LT10/LT11/LT12 restore the parcel; LT11 skips a dispute already in mediation")
    void closingsRestoreTheParcel() {
        Dispute withdrawn = stored(Dispute.open(open(DisputeCause.PACKAGE_DAMAGED, parcelId, null),
                DisputeReference.forSequence(2)));
        service.withdrawDispute(new WithdrawDisputeCommand(withdrawn.getId(), TENANT, CLAIMANT)).block();
        verify(parcels).applyOutcome(eq(TENANT), eq(parcelId), eq(withdrawn.getId().getValue()),
                eq("RESTORE_PREVIOUS"), eq("DISPUTE_WITHDRAWN"), isNull(), isNull(), any());

        Dispute expired = stored(Dispute.open(open(DisputeCause.PACKAGE_DAMAGED, parcelId, null),
                DisputeReference.forSequence(4)));
        assertThat(service.expire(expired.getId(), TENANT).block().getStatus())
                .isEqualTo(DisputeStatus.CLOSED_EXPIRED);
        verify(parcels).applyOutcome(eq(TENANT), eq(parcelId), eq(expired.getId().getValue()),
                eq("RESTORE_PREVIOUS"), eq("DISPUTE_EXPIRED"), isNull(), isNull(), any());

        Dispute mediation = mediationOnParcel();
        assertThat(service.expire(mediation.getId(), TENANT).block()).isNull();
        assertThat(mediation.getStatus()).isEqualTo(DisputeStatus.MEDIATION_IN_PROGRESS);

        Dispute closed = stored(Dispute.open(open(DisputeCause.PACKAGE_DAMAGED, parcelId, null),
                DisputeReference.forSequence(5)));
        service.closeDispute(close(closed, ClosureType.ADMINISTRATIVE_CLOSURE, "doublon")).block();
        verify(parcels).applyOutcome(eq(TENANT), eq(parcelId), eq(closed.getId().getValue()),
                eq("RESTORE_PREVIOUS"), eq("DISPUTE_ADMINISTRATIVE_CLOSURE"), isNull(), isNull(), any());
        StepVerifier.create(service.closeDispute(close(closed, ClosureType.RESOLVED_WITH_COMPENSATION, "x")))
                .expectError(DisputeStateException.class).verify();
    }
}
