package com.yowyob.tiibntick.core.geo.domain;

import com.yowyob.tiibntick.core.geo.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg geo  extensions.
 *
 * @author MANFOUO Braun
 */
class FreelancerOrgGeoTest {

    @Nested
    @DisplayName("ZoneAccessDifficulty")
    class ZoneAccessDifficultyTests {
        @Test
        void allValuesExist() {
            assertThat(ZoneAccessDifficulty.EASY).isNotNull();
            assertThat(ZoneAccessDifficulty.MODERATE).isNotNull();
            assertThat(ZoneAccessDifficulty.DIFFICULT).isNotNull();
            assertThat(ZoneAccessDifficulty.INACCESSIBLE).isNotNull();
        }
    }

    @Nested
    @DisplayName("DeliveryZoneType")
    class DeliveryZoneTypeTests {
        @Test
        void allValuesExist() {
            assertThat(DeliveryZoneType.URBAN).isNotNull();
            assertThat(DeliveryZoneType.PERI_URBAN).isNotNull();
            assertThat(DeliveryZoneType.RURAL).isNotNull();
            assertThat(DeliveryZoneType.INTER_CITY).isNotNull();
            assertThat(DeliveryZoneType.REMOTE).isNotNull();
        }
    }

    @Nested
    @DisplayName("ServiceZonePolygon — FreelancerOrg owner")
    class FreelancerOrgZone {

        @Test
        @DisplayName("createForFreelancerOrg should set FREELANCER_ORG owner type")
        void createFreelancerOrgZone() {
            UUID tenantId = UUID.randomUUID();
            String orgId = "FRL-ORG-001";
            List<GeoPoint> vertices = List.of(
                    GeoPoint.of(3.86, 11.50),
                    GeoPoint.of(3.87, 11.50),
                    GeoPoint.of(3.87, 11.51),
                    GeoPoint.of(3.86, 11.51));

            ServiceZonePolygon zone = ServiceZonePolygon.createForFreelancerOrg(
                    tenantId, orgId, "Centre Yaoundé Coverage", vertices);

            assertThat(zone.freelancerOrgId()).isEqualTo(orgId);
            assertThat(zone.ownerType()).isEqualTo("FREELANCER_ORG");
            assertThat(zone.isOwnedByFreelancerOrg()).isTrue();
            assertThat(zone.agencyId()).isNull();
            assertThat(zone.isActive()).isTrue();
        }

        @Test
        @DisplayName("Standard create() should default to AGENCY owner type")
        void createAgencyZoneDefaultsToAgency() {
            UUID tenantId = UUID.randomUUID();
            UUID agencyId = UUID.randomUUID();
            List<GeoPoint> vertices = List.of(
                    GeoPoint.of(3.86, 11.50),
                    GeoPoint.of(3.87, 11.50),
                    GeoPoint.of(3.87, 11.51));

            ServiceZonePolygon zone = ServiceZonePolygon.create(tenantId, agencyId, "Zone Test", vertices);

            assertThat(zone.ownerType()).isEqualTo("AGENCY");
            assertThat(zone.isOwnedByFreelancerOrg()).isFalse();
            assertThat(zone.agencyId()).isEqualTo(agencyId);
        }

        @Test
        @DisplayName("Point-in-polygon should work for FreelancerOrg zones")
        void pointInPolygonForFreelancerOrgZone() {
            UUID tenantId = UUID.randomUUID();
            // Bounding box around point (3.865, 11.505)
            List<GeoPoint> vertices = List.of(
                    GeoPoint.of(3.860, 11.500),
                    GeoPoint.of(3.870, 11.500),
                    GeoPoint.of(3.870, 11.510),
                    GeoPoint.of(3.860, 11.510));

            ServiceZonePolygon zone = ServiceZonePolygon.createForFreelancerOrg(
                    tenantId, "FRL-001", "Test Zone", vertices);

            assertThat(zone.contains(GeoPoint.of(3.865, 11.505))).isTrue();
            assertThat(zone.contains(GeoPoint.of(3.900, 11.600))).isFalse();
        }
    }
}
