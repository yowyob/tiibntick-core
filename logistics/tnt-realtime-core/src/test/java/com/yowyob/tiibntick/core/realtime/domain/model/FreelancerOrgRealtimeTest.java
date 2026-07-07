package com.yowyob.tiibntick.core.realtime.domain.model;

import com.yowyob.tiibntick.core.realtime.adapter.in.websocket.dto.GpsPingMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to tnt-realtime-core domain models.
 *
 * @author MANFOUO Braun
 */
class FreelancerOrgRealtimeTest {

    @Nested @DisplayName("GPSStreamEntry — freelancerOrgId")
    class GPSStreamEntryTests {

        @Test @DisplayName("hasFreelancerOrg() should return true when orgId is set")
        void hasFreelancerOrgWhenSet() {
            GPSStreamEntry e = new GPSStreamEntry("d-1","m-1","t-1",
                    GeoCoordinates.of(3.86,11.50),30.0,90.0,5.0,85,LocalDateTime.now(),"FRL-001");
            assertThat(e.hasFreelancerOrg()).isTrue();
            assertThat(e.freelancerOrgId()).isEqualTo("FRL-001");
        }

        @Test @DisplayName("hasFreelancerOrg() should return false when null")
        void hasFreelancerOrgWhenNull() {
            GPSStreamEntry e = new GPSStreamEntry("d-1","m-1","t-1",
                    GeoCoordinates.of(3.86,11.50),30.0,90.0,5.0,85,LocalDateTime.now(),null);
            assertThat(e.hasFreelancerOrg()).isFalse();
        }

        @Test @DisplayName("of() factory defaults orgId to null")
        void staticFactoryDefaultsOrgToNull() {
            GPSStreamEntry e = GPSStreamEntry.of("d-1","m-1","t-1",
                    GeoCoordinates.of(3.86,11.50),30.0,90.0,5.0,null,LocalDateTime.now());
            assertThat(e.freelancerOrgId()).isNull();
        }
    }

    @Nested @DisplayName("GpsPingMessage — freelancerOrgId")
    class GpsPingMessageTests {

        @Test @DisplayName("Should carry freelancerOrgId")
        void carrierId() {
            GpsPingMessage msg = new GpsPingMessage(
                    "d-1","m-1",3.86,11.50,null,25.0,90.0,5.0,80,
                    System.currentTimeMillis(),"FRL-001");
            assertThat(msg.freelancerOrgId()).isEqualTo("FRL-001");
        }

        @Test @DisplayName("freelancerOrgId is nullable for standard deliverers")
        void nullable() {
            GpsPingMessage msg = new GpsPingMessage(
                    "d-1","m-1",3.86,11.50,null,25.0,90.0,5.0,80,
                    System.currentTimeMillis(),null);
            assertThat(msg.freelancerOrgId()).isNull();
        }
    }

    @Nested @DisplayName("BroadcastTopic — FreelancerOrg fleet topics")
    class BroadcastTopicTests {

        @Test @DisplayName("forFreelancerOrgFleet produces correct path")
        void fleetTopicPath() {
            assertThat(BroadcastTopic.forFreelancerOrgFleet("FRL-001").path())
                    .isEqualTo("/topic/fleet/FRL-001");
        }

        @Test @DisplayName("forSubDeliverer produces correct path")
        void subDelivererPath() {
            assertThat(BroadcastTopic.forSubDeliverer("FRL-001","SUB-001").path())
                    .isEqualTo("/topic/fleet/FRL-001/sub/SUB-001");
        }

        @Test @DisplayName("Original topics still work")
        void originalTopicsWork() {
            assertThat(BroadcastTopic.forDelivery("M-001").path()).isEqualTo("/topic/delivery/M-001");
            assertThat(BroadcastTopic.forTracking("TRK-001").path()).isEqualTo("/topic/tracking/TRK-001");
        }
    }
}
