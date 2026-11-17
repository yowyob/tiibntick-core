package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.RelayPointPublicView;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("A delivery shows the relay point its sender chose")
class RelayTargetResolverTest {

    @Mock private IDeliveryNeedRepository needs;
    @Mock private GofpRelayPointRepository relayPoints;
    @Mock private RelayPointDirectoryService directory;

    private final UUID needId = UUID.randomUUID();
    private final UUID hub = UUID.randomUUID();

    private DeliveryResponseDTO delivery() {
        DeliveryResponseDTO d = new DeliveryResponseDTO();
        d.setId(UUID.randomUUID());
        d.setDeliveryNeedId(needId);
        return d;
    }

    private RelayTargetResolver resolver() {
        return new RelayTargetResolver(needs, relayPoints, directory);
    }

    @Test
    @DisplayName("the chosen relay point (by hub id) is added with its public view")
    void chosenRelayPoint_isAdded() {
        DeliveryNeed need = new DeliveryNeed();
        need.setId(needId);
        need.setTargetRelayPointId(hub);
        GofpRelayPoint rp = new GofpRelayPoint();
        rp.setId(UUID.randomUUID());
        rp.setCoreRelayPointId(hub);
        rp.setName("Chez Awa");
        RelayPointPublicView view = new RelayPointPublicView(rp.getId(), hub, "Chez Awa", null,
                "Rue 1", "Bastos", "Yaoundé", null, 3.88, 11.5, null, null, null, null);
        when(needs.findById(needId)).thenReturn(Mono.just(need));
        when(relayPoints.findById(hub)).thenReturn(Mono.empty());
        when(relayPoints.findByCoreRelayPointId(hub)).thenReturn(Mono.just(rp));
        when(directory.publicView(rp)).thenReturn(Mono.just(view));

        StepVerifier.create(resolver().withTarget(delivery()))
                .assertNext(d -> {
                    assertThat(d.getTargetRelayPointId()).isEqualTo(hub);
                    assertThat(d.getTargetRelayPoint()).isEqualTo(view);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a delivery to the recipient carries no relay point")
    void noTarget_unchanged() {
        DeliveryNeed need = new DeliveryNeed();
        need.setId(needId);
        when(needs.findById(needId)).thenReturn(Mono.just(need));

        StepVerifier.create(resolver().withTarget(delivery()))
                .assertNext(d -> {
                    assertThat(d.getTargetRelayPointId()).isNull();
                    assertThat(d.getTargetRelayPoint()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a lookup failure never fails the read of the delivery")
    void lookupFailure_unchanged() {
        when(needs.findById(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(resolver().withTarget(delivery()))
                .assertNext(d -> assertThat(d.getTargetRelayPoint()).isNull())
                .verifyComplete();
    }
}
