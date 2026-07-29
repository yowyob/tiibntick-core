package com.yowyob.tiibntick.delivery.domain.valueobject;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.TrackingCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@code TrackingCode} value object used by {@code Delivery.create()}.
 *
 * @author MANFOUO Braun
 */
class TrackingCodeTest {

    @Test
    @DisplayName("generate() produces a code matching TNT-YYYYMMDD-XXXXXXXX")
    void generateProducesValidFormat() {
        TrackingCode code = TrackingCode.generate();
        assertThat(code.value()).matches("TNT-\\d{8}-[A-Z0-9]{8}");
    }

    @Test
    @DisplayName("generate() produces distinct codes across many calls")
    void generateProducesDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(TrackingCode.generate().value());
        }
        assertThat(codes).hasSize(1000);
    }

    @Test
    @DisplayName("Constructor rejects a value not matching the expected format")
    void rejectsInvalidFormat() {
        assertThatThrownBy(() -> new TrackingCode("NOT-A-VALID-CODE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString() returns the raw code value")
    void toStringReturnsValue() {
        TrackingCode code = TrackingCode.generate();
        assertThat(code.toString()).isEqualTo(code.value());
    }
}
