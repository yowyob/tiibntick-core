package com.yowyob.tiibntick.core.trust.adapter.out.actor;

import com.yowyob.tiibntick.core.actor.application.port.out.BadgeAnchorPayload;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordBadgeUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BadgeAnchorAdapter}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BadgeAnchorAdapter — IBadgeAnchorPort implementation")
class BadgeAnchorAdapterTest {

    @Mock
    private RecordBadgeUseCase recordBadge;

    @Test
    @DisplayName("should delegate to RecordBadgeUseCase with the payload's identifiers and return the tx hash")
    void shouldDelegateToRecordBadgeUseCase() {
        final BadgeAnchorAdapter adapter = new BadgeAnchorAdapter(recordBadge);

        final UUID tenantId = UUID.randomUUID();
        final UUID actorId = UUID.randomUUID();
        final BadgeAnchorPayload payload = new BadgeAnchorPayload(
                tenantId, actorId, "100_DELIVERIES", "100 Deliveries");

        when(recordBadge.record(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just("tx-hash-001"));

        StepVerifier.create(adapter.anchor(payload))
                .expectNext("tx-hash-001")
                .verifyComplete();

        final ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> badgeCodeCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Integer> pointsCaptor = ArgumentCaptor.forClass(Integer.class);
        final ArgumentCaptor<String> tenantIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(recordBadge).record(actorIdCaptor.capture(), badgeCodeCaptor.capture(),
                pointsCaptor.capture(), tenantIdCaptor.capture());

        assertThat(actorIdCaptor.getValue()).isEqualTo(actorId.toString());
        assertThat(badgeCodeCaptor.getValue()).isEqualTo("100_DELIVERIES");
        assertThat(pointsCaptor.getValue()).isEqualTo(0);
        assertThat(tenantIdCaptor.getValue()).isEqualTo(tenantId.toString());
    }
}
