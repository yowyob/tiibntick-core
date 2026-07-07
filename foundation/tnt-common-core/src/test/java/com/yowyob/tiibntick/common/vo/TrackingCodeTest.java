package com.yowyob.tiibntick.common.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TrackingCode}.
 *
 * Author: MANFOUO Braun
 */
class TrackingCodeTest {

    @Test
    @DisplayName("generate produces valid code with correct format")
    void generate_producesValidFormat() {
        TrackingCode code = TrackingCode.generate("TNT");
        assertThat(code.getValue()).matches("[A-Z]{2,5}-\\d{8}-[A-Z0-9]{6}");
        assertThat(code.getPrefix()).isEqualTo("TNT");
    }

    @Test
    @DisplayName("generateSequential produces zero-padded sequential suffix")
    void generateSequential_producesZeroPaddedSuffix() {
        TrackingCode code = TrackingCode.generateSequential("DEL", 42L);
        assertThat(code.getSuffix()).isEqualTo("000042");
    }

    @Test
    @DisplayName("of parses a valid tracking code string")
    void of_parsesValidString() {
        TrackingCode code = TrackingCode.of("TNT-20251001-A3BX92");
        assertThat(code.getValue()).isEqualTo("TNT-20251001-A3BX92");
        assertThat(code.getPrefix()).isEqualTo("TNT");
        assertThat(code.getDatePart()).isEqualTo("20251001");
        assertThat(code.getSuffix()).isEqualTo("A3BX92");
    }

    @Test
    @DisplayName("of normalizes to uppercase")
    void of_normalizesToUppercase() {
        TrackingCode code = TrackingCode.of("tnt-20251001-a3bx92");
        assertThat(code.getValue()).isEqualTo("TNT-20251001-A3BX92");
    }

    @Test
    @DisplayName("of rejects invalid format")
    void of_rejectsInvalidFormat() {
        assertThatThrownBy(() -> TrackingCode.of("INVALID"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid TrackingCode format");

        assertThatThrownBy(() -> TrackingCode.of(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isValid returns correct result")
    void isValid_returnsCorrectResult() {
        assertThat(TrackingCode.isValid("TNT-20251001-A3BX92")).isTrue();
        assertThat(TrackingCode.isValid("PKG-20251015-000042")).isTrue();
        assertThat(TrackingCode.isValid("INVALID")).isFalse();
        assertThat(TrackingCode.isValid(null)).isFalse();
        assertThat(TrackingCode.isValid("")).isFalse();
    }

    @Test
    @DisplayName("equals and hashCode based on value")
    void equalsAndHashCode_basedOnValue() {
        TrackingCode c1 = TrackingCode.of("TNT-20251001-A3BX92");
        TrackingCode c2 = TrackingCode.of("TNT-20251001-A3BX92");
        TrackingCode c3 = TrackingCode.of("DEL-20251001-A3BX92");

        assertThat(c1).isEqualTo(c2);
        assertThat(c1).isNotEqualTo(c3);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }

    @Test
    @DisplayName("generate rejects blank or invalid prefix")
    void generate_rejectsInvalidPrefix() {
        assertThatThrownBy(() -> TrackingCode.generate(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingCode.generate("123"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingCode.generate("TOOLONG"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
