package com.yowyob.tiibntick.core.realtime.domain.service;

import com.yowyob.tiibntick.core.realtime.application.port.out.IPresenceRepository;
import com.yowyob.tiibntick.core.realtime.application.port.out.IWebSocketBroadcaster;
import com.yowyob.tiibntick.core.realtime.domain.model.DeviceInfo;
import com.yowyob.tiibntick.core.realtime.domain.model.GeoCoordinates;
import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.DeviceType;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.PresenceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceDomainServiceTest {

    @Mock private IPresenceRepository presenceRepository;
    @Mock private IWebSocketBroadcaster broadcaster;

    private PresenceDomainService presenceService;

    private static final String USER_ID = "user-123";
    private static final String TENANT_ID = "tenant-A";
    private static final DeviceInfo DEVICE_INFO = DeviceInfo.of(DeviceType.ANDROID, "2.0", "Android 14");

    @BeforeEach
    void setUp() {
        presenceService = new PresenceDomainService(presenceRepository, broadcaster);
    }

    @Test
    @DisplayName("markOnline() saves a new presence record with ONLINE_AVAILABLE status")
    void markOnlineSavesPresenceRecord() {
        when(presenceRepository.save(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        ArgumentCaptor<PresenceRecord> captor = ArgumentCaptor.forClass(PresenceRecord.class);

        StepVerifier.create(presenceService.markOnline(USER_ID, TENANT_ID, DEVICE_INFO))
                .verifyComplete();

        verify(presenceRepository).save(captor.capture());
        PresenceRecord saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getStatus()).isEqualTo(PresenceStatus.ONLINE_AVAILABLE);
        assertThat(saved.isOnline()).isTrue();
    }

    @Test
    @DisplayName("markOnline() broadcasts presence change to tenant presence topic")
    void markOnlineBroadcastsToPresenceTopic() {
        when(presenceRepository.save(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(presenceService.markOnline(USER_ID, TENANT_ID, DEVICE_INFO))
                .verifyComplete();

        verify(broadcaster).broadcast(any(), any());
    }

    @Test
    @DisplayName("markOffline() updates existing record status to OFFLINE")
    void markOfflineUpdatesRecord() {
        PresenceRecord existing = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);
        when(presenceRepository.findByUserAndTenant(USER_ID, TENANT_ID)).thenReturn(Mono.just(existing));
        when(presenceRepository.save(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(presenceService.markOffline(USER_ID, TENANT_ID))
                .verifyComplete();

        assertThat(existing.getStatus()).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(existing.isOnline()).isFalse();
        verify(presenceRepository).save(existing);
    }

    @Test
    @DisplayName("markOffline() completes without error when no record exists")
    void markOfflineHandlesMissingRecord() {
        when(presenceRepository.findByUserAndTenant(USER_ID, TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(presenceService.markOffline(USER_ID, TENANT_ID))
                .verifyComplete();
    }

    @Test
    @DisplayName("assignMission() transitions status to ONLINE_ON_MISSION")
    void assignMissionTransitionsStatus() {
        PresenceRecord existing = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);
        when(presenceRepository.findByUserAndTenant(USER_ID, TENANT_ID)).thenReturn(Mono.just(existing));
        when(presenceRepository.save(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(presenceService.assignMission(USER_ID, TENANT_ID, "MISSION-001"))
                .verifyComplete();

        assertThat(existing.getStatus()).isEqualTo(PresenceStatus.ONLINE_ON_MISSION);
        assertThat(existing.getActiveMissionId()).isEqualTo("MISSION-001");
    }

    @Test
    @DisplayName("clearMission() transitions status back to ONLINE_AVAILABLE")
    void clearMissionTransitionsBackToAvailable() {
        PresenceRecord existing = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);
        existing.assignMission("MISSION-001");
        when(presenceRepository.findByUserAndTenant(USER_ID, TENANT_ID)).thenReturn(Mono.just(existing));
        when(presenceRepository.save(any())).thenReturn(Mono.empty());
        when(broadcaster.broadcast(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(presenceService.clearMission(USER_ID, TENANT_ID))
                .verifyComplete();

        assertThat(existing.getStatus()).isEqualTo(PresenceStatus.ONLINE_AVAILABLE);
        assertThat(existing.getActiveMissionId()).isNull();
    }

    @Test
    @DisplayName("isOnline() returns true when record exists and is online")
    void isOnlineReturnsTrueForOnlineActor() {
        PresenceRecord existing = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);
        when(presenceRepository.findByUserAndTenant(USER_ID, TENANT_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(presenceService.isOnline(USER_ID, TENANT_ID))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("isOnline() returns false when no record in Redis")
    void isOnlineReturnsFalseWhenNoRecord() {
        when(presenceRepository.findByUserAndTenant(USER_ID, TENANT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(presenceService.isOnline(USER_ID, TENANT_ID))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("updateCoordinates() updates location in the presence record")
    void updateCoordinatesUpdatesLocation() {
        PresenceRecord existing = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);
        GeoCoordinates newCoords = GeoCoordinates.of(3.848, 11.502);
        when(presenceRepository.findByUserAndTenant(USER_ID, TENANT_ID)).thenReturn(Mono.just(existing));
        when(presenceRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(presenceService.updateCoordinates(USER_ID, TENANT_ID, newCoords))
                .verifyComplete();

        assertThat(existing.getCurrentCoordinates()).isEqualTo(newCoords);
    }

    @Test
    @DisplayName("PresenceRecord.isStale() returns true after silence duration")
    void presenceRecordIsStaleAfterSilence() throws InterruptedException {
        PresenceRecord record = new PresenceRecord(USER_ID, TENANT_ID, DEVICE_INFO);
        assertThat(record.isStale(Duration.ZERO)).isTrue();
    }
}
