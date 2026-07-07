package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IGeofenceZoneRepository;
import com.yowyob.tiibntick.core.realtime.application.port.out.IRealtimeEventPublisher;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.event.GeofenceTriggerEvent;
import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.GeofenceZone;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.GeofenceZoneType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GeofenceMonitorService}.
 * Verifies ENTER/EXIT zone crossing detection and event emission.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class GeofenceMonitorServiceTest {

    @Mock private IGeofenceZoneRepository zoneRepository;
    @Mock private IWebSocketBroadcaster broadcaster;
    @Mock private IRealtimeEventPublisher eventPublisher;

    private GeofenceMonitorService monitorService;

    // Relay hub center in Yaoundé, Cameroun
    private static final GeoCoordinates HUB_CENTER = GeoCoordinates.of(3.8480, 11.5021);
    private static final double HUB_RADIUS_METERS = 300.0;
    private static final String TENANT_ID = "tenant-A";
    private static final String DELIVERER_ID = "d-001";
    private static final String MISSION_ID = "M-001";

    private GeofenceZone relayHubZone;

    @BeforeEach
    void setUp() {
        monitorService = new GeofenceMonitorService(zoneRepository, broadcaster, eventPublisher);

        relayHubZone = GeofenceZone.builder()
                .id("zone-hub-1")
                .tenantId(TENANT_ID)
                .name("Hub Central Yaoundé")
                .center(HUB_CENTER)
                .radiusMeters(HUB_RADIUS_METERS)
                .type(GeofenceZoneType.RELAY_HUB)
                .linkedEntityId("hub-001")
                .build();
    }

    private GPSStreamEntry pingAt(GeoCoordinates coords) {
        return GPSStreamEntry.of(
                DELIVERER_ID, MISSION_ID, TENANT_ID,
                coords, 20.0, 180.0, 10.0, null,
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("ENTER trigger emitted when deliverer moves inside zone radius")
    void enterTriggerEmittedWhenInsideZone() {
        when(zoneRepository.findActiveByTenant(TENANT_ID)).thenReturn(Flux.just(relayHubZone));
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        // Position inside the hub zone (same as center)
        GPSStreamEntry pingInside = pingAt(HUB_CENTER);

        StepVerifier.create(monitorService.checkGeofences(pingInside))
                .verifyComplete();

        ArgumentCaptor<GeofenceTriggerEvent> eventCaptor = ArgumentCaptor.forClass(GeofenceTriggerEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        GeofenceTriggerEvent event = eventCaptor.getValue();
        assertThat(event.getTrigger().isEntry()).isTrue();
        assertThat(event.getTrigger().actorId()).isEqualTo(DELIVERER_ID);
        assertThat(event.getTrigger().zoneId()).isEqualTo("zone-hub-1");
    }

    @Test
    @DisplayName("EXIT trigger emitted when deliverer moves outside zone radius")
    void exitTriggerEmittedWhenOutsideZone() {
        when(zoneRepository.findActiveByTenant(TENANT_ID)).thenReturn(Flux.just(relayHubZone));
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        // First ping: inside the zone
        monitorService.checkGeofences(pingAt(HUB_CENTER)).block();

        // Second ping: outside the zone (~500m away)
        GeoCoordinates outside = GeoCoordinates.of(3.8525, 11.5021);
        StepVerifier.create(monitorService.checkGeofences(pingAt(outside)))
                .verifyComplete();

        // First publish = ENTER, second = EXIT
        verify(eventPublisher, times(2)).publish(any());
    }

    @Test
    @DisplayName("no trigger emitted when deliverer stays outside zone")
    void noTriggerWhenStaysOutside() {
        when(zoneRepository.findActiveByTenant(TENANT_ID)).thenReturn(Flux.just(relayHubZone));

        GeoCoordinates outside = GeoCoordinates.of(3.8600, 11.5200);

        StepVerifier.create(monitorService.checkGeofences(pingAt(outside)))
                .verifyComplete();

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("no duplicate ENTER triggers when deliverer stays inside zone")
    void noDuplicateEnterTriggers() {
        when(zoneRepository.findActiveByTenant(TENANT_ID)).thenReturn(Flux.just(relayHubZone));
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        // First ping: enter
        monitorService.checkGeofences(pingAt(HUB_CENTER)).block();
        // Second ping: still inside
        monitorService.checkGeofences(pingAt(HUB_CENTER)).block();

        verify(eventPublisher, times(1)).publish(any()); // only one ENTER
    }

    @Test
    @DisplayName("clearDelivererState() resets zone membership — next entry triggers ENTER again")
    void clearDelivererStateResetsZoneMembership() {
        when(zoneRepository.findActiveByTenant(TENANT_ID)).thenReturn(Flux.just(relayHubZone));
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        // Enter zone
        monitorService.checkGeofences(pingAt(HUB_CENTER)).block();
        // Clear state (e.g. mission completed)
        monitorService.clearDelivererState(DELIVERER_ID);
        // Enter zone again — should trigger a new ENTER event
        monitorService.checkGeofences(pingAt(HUB_CENTER)).block();

        verify(eventPublisher, times(2)).publish(any()); // two ENTER events
    }

    @Test
    @DisplayName("GeofenceZone.contains() returns true for coordinates inside radius")
    void zoneContainsInsideCoords() {
        assertThat(relayHubZone.contains(HUB_CENTER)).isTrue();
        // 100m offset — inside 300m radius
        GeoCoordinates nearby = GeoCoordinates.of(3.8489, 11.5021);
        assertThat(relayHubZone.contains(nearby)).isTrue();
    }

    @Test
    @DisplayName("GeofenceZone.contains() returns false for coordinates outside radius")
    void zoneDoesNotContainOutsideCoords() {
        // ~500m north of hub center — outside 300m radius
        GeoCoordinates farNorth = GeoCoordinates.of(3.8525, 11.5021);
        assertThat(relayHubZone.contains(farNorth)).isFalse();
    }
}
