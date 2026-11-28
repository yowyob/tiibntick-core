package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsType;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.LogisticsVehicleTypeMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsVehicleTypeMappingTest {

    /** The resource-core VehicleType names (copied as strings: delivery-core does not depend on resource-core). */
    private static final Set<String> RESOURCE_NAMES = Set.of("MOTORCYCLE", "BICYCLE", "CAR", "TRICYCLE", "VAN",
            "TRUCK", "MOTO", "VELO", "VOITURE", "CAMIONNETTE", "VELO_CARGO");

    @Test
    void theGoFreelancerVehicleVocabularyIsCoveredByLogisticsType() {
        for (String go : new String[]{"BIKE", "MOTORBIKE", "CAR", "VAN", "TRUCK", "SCOOTER", "TRICYCLE", "BUS"}) {
            assertThat(LogisticsType.valueOf(go)).as(go).isNotNull();
        }
    }

    @ParameterizedTest
    @EnumSource(LogisticsType.class)
    void everyMappedLogisticsTypeRoundTrips(LogisticsType type) {
        LogisticsVehicleTypeMapping.toResourceVehicleType(type).ifPresent(resource -> {
            assertThat(RESOURCE_NAMES).contains(resource);
            assertThat(LogisticsVehicleTypeMapping.fromResourceVehicleType(resource)).contains(type);
        });
    }

    @Test
    void scooterAndBusHaveNoResourceCounterpart() {
        assertThat(LogisticsVehicleTypeMapping.toResourceVehicleType(LogisticsType.SCOOTER)).isEmpty();
        assertThat(LogisticsVehicleTypeMapping.toResourceVehicleType(LogisticsType.BUS)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"MOTO,MOTORBIKE", "velo,BIKE", "VELO_CARGO,BIKE", "VOITURE,CAR", "CAMIONNETTE,VAN", "TRUCK,TRUCK"})
    void everyResourceNameMapsToALogisticsType(String resource, LogisticsType expected) {
        assertThat(LogisticsVehicleTypeMapping.fromResourceVehicleType(resource)).contains(expected);
    }

    @Test
    void allResourceNamesAreKnown() {
        RESOURCE_NAMES.forEach(n -> assertThat(LogisticsVehicleTypeMapping.fromResourceVehicleType(n)).as(n).isPresent());
        assertThat(LogisticsVehicleTypeMapping.fromResourceVehicleType("HOVERCRAFT")).isEmpty();
    }
}
