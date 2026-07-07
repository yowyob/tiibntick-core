package com.yowyob.tiibntick.common.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PhoneNumber}.
 *
 * Author: MANFOUO Braun
 */
class PhoneNumberTest {

    @Test
    @DisplayName("of accepts valid E.164 format")
    void of_acceptsValidE164() {
        PhoneNumber phone = PhoneNumber.of("+237677123456");
        assertThat(phone.getValue()).isEqualTo("+237677123456");
    }

    @Test
    @DisplayName("of rejects invalid E.164")
    void of_rejectsInvalidE164() {
        assertThatThrownBy(() -> PhoneNumber.of("677123456"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumber.of(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumber.of("+0"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ofCameroon normalizes 9-digit local number")
    void ofCameroon_normalizesLocalNumber() {
        PhoneNumber phone = PhoneNumber.ofCameroon("677123456");
        assertThat(phone.getValue()).isEqualTo("+237677123456");
        assertThat(phone.isCameroonian()).isTrue();
    }

    @Test
    @DisplayName("ofCameroon accepts already-E.164 Cameroon number")
    void ofCameroon_acceptsAlreadyE164() {
        PhoneNumber phone = PhoneNumber.ofCameroon("+237677123456");
        assertThat(phone.getValue()).isEqualTo("+237677123456");
    }

    @Test
    @DisplayName("ofNigeria normalizes local number")
    void ofNigeria_normalizesLocalNumber() {
        PhoneNumber phone = PhoneNumber.ofNigeria("8012345678");
        assertThat(phone.getValue()).isEqualTo("+2348012345678");
        assertThat(phone.isNigerian()).isTrue();
    }

    @Test
    @DisplayName("getMasked returns GDPR-compliant masked value")
    void getMasked_returnsMaskedValue() {
        PhoneNumber phone = PhoneNumber.of("+237677123456");
        String masked = phone.getMasked();
        assertThat(masked).contains("***");
        assertThat(masked).doesNotContain("677123456");
        assertThat(masked).startsWith("+237");
    }

    @Test
    @DisplayName("toString returns masked value — never raw E.164")
    void toString_returnsMaskedValue() {
        PhoneNumber phone = PhoneNumber.ofCameroon("677123456");
        // toString must NOT expose the raw number
        assertThat(phone.toString()).contains("***");
        assertThat(phone.toString()).doesNotContain("677123456");
    }

    @Test
    @DisplayName("equals and hashCode based on E.164 value")
    void equalsAndHashCode() {
        PhoneNumber p1 = PhoneNumber.of("+237677123456");
        PhoneNumber p2 = PhoneNumber.ofCameroon("677123456");
        PhoneNumber p3 = PhoneNumber.of("+237699000000");

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        assertThat(p1).isNotEqualTo(p3);
    }

    @Test
    @DisplayName("getSubscriberNumber returns number without country code")
    void getSubscriberNumber() {
        PhoneNumber phone = PhoneNumber.of("+237677123456");
        assertThat(phone.getSubscriberNumber()).isEqualTo("677123456");
    }

    @Test
    @DisplayName("isValid returns correct result")
    void isValid() {
        assertThat(PhoneNumber.isValid("+237677123456")).isTrue();
        assertThat(PhoneNumber.isValid("+2348012345678")).isTrue();
        assertThat(PhoneNumber.isValid("677123456")).isFalse();
        assertThat(PhoneNumber.isValid(null)).isFalse();
    }
}
