package com.yowyob.tiibntick.core.tp.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.tp.application.port.in.command.EarnLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.service.LoyaltyService;
import com.yowyob.tiibntick.core.tp.application.service.TntClientProfileService;
import com.yowyob.tiibntick.core.tp.domain.model.LoyaltyAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** H4: the completed-delivery message produced by delivery-core is read (aggregateId = mission, senderId = client). */
class DeliveryEventConsumerTest {

    private final LoyaltyService loyalty = mock(LoyaltyService.class);
    private final TntClientProfileService profiles = mock(TntClientProfileService.class);
    private final DeliveryEventConsumer consumer = new DeliveryEventConsumer(loyalty, profiles, new ObjectMapper());

    @Test
    void readsTheDeliveryCoreBodyWithNestedPayload() {
        UUID tenant = UUID.randomUUID(), delivery = UUID.randomUUID(), sender = UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "eventType", "DeliveryCompletedEvent", "aggregateId", delivery.toString(),
                "tenantId", tenant.toString(), "occurredAt", "2026-11-24T10:00:00Z",
                "payload", Map.of("aggregateId", delivery.toString(), "tenantId", tenant.toString(),
                        "deliveryPersonId", UUID.randomUUID().toString(), "senderId", sender.toString()));
        assertThat(DeliveryEventConsumer.parse(body))
                .isEqualTo(new DeliveryEventConsumer.CompletedMission(tenant, sender, delivery.toString()));
    }

    @Test
    void keepsTheHistoricalFlatShape() {
        UUID tenant = UUID.randomUUID(), sender = UUID.randomUUID();
        assertThat(DeliveryEventConsumer.parse(Map.of("tenantId", tenant.toString(),
                "senderThirdPartyId", sender.toString(), "missionId", "M-1")))
                .isEqualTo(new DeliveryEventConsumer.CompletedMission(tenant, sender, "M-1"));
    }

    @Test
    void withoutSenderNothingIsCredited() {
        assertThat(DeliveryEventConsumer.parse(Map.of("tenantId", UUID.randomUUID().toString(),
                "aggregateId", UUID.randomUUID().toString()))).isNull();
    }

    @Test
    void creditsThroughTheSystemPathAndToleratesAMissingTpProfile() {
        UUID tenant = UUID.randomUUID(), sender = UUID.randomUUID();
        when(loyalty.creditCompletedDelivery(any())).thenReturn(Mono.just(LoyaltyAccount.create(tenant, sender)));
        // no tp-core profile (Go-Freelancer client): the increment completes empty
        when(profiles.incrementDeliveriesIfProfiled(tenant, sender)).thenReturn(Mono.empty());

        StepVerifier.create(consumer.handle(new DeliveryEventConsumer.CompletedMission(tenant, sender, "D-1")))
                .verifyComplete();

        ArgumentCaptor<EarnLoyaltyPointsCommand> cmd = ArgumentCaptor.forClass(EarnLoyaltyPointsCommand.class);
        verify(loyalty).creditCompletedDelivery(cmd.capture());
        verify(loyalty, never()).earn(any());
        assertThat(cmd.getValue()).isEqualTo(new EarnLoyaltyPointsCommand(tenant, sender, 10, "D-1"));
    }

    /** Lot L3 (C4): a mission already credited (redelivered message) does not count a second delivery. */
    @Test
    void aMissionAlreadyCreditedIsNotCountedAgain() {
        UUID tenant = UUID.randomUUID(), sender = UUID.randomUUID();
        when(loyalty.creditCompletedDelivery(any())).thenReturn(Mono.empty());

        StepVerifier.create(consumer.handle(new DeliveryEventConsumer.CompletedMission(tenant, sender, "D-9")))
                .verifyComplete();

        verify(profiles, never()).incrementDeliveriesIfProfiled(any(), any());
    }

    /** Lot L8 (G.8): a failed credit is no longer swallowed, it reaches the container (retries, then tnt.dlq). */
    @Test
    void aFailedCreditReachesTheContainer() {
        UUID tenant = UUID.randomUUID(), sender = UUID.randomUUID();
        when(loyalty.creditCompletedDelivery(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(consumer.handle(new DeliveryEventConsumer.CompletedMission(tenant, sender, "D-7")))
                .verifyErrorMessage("db down");
        verify(profiles, never()).incrementDeliveriesIfProfiled(any(), any());
    }
}
