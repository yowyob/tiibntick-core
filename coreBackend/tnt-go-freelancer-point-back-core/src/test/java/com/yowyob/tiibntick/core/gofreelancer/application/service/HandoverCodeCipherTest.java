package com.yowyob.tiibntick.core.gofreelancer.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("C-53 — handover codes encryption")
class HandoverCodeCipherTest {

    @Test
    @DisplayName("round trip; a random IV makes two encryptions of the same code differ")
    void roundTrip() {
        HandoverCodeCipher c = new HandoverCodeCipher("some-server-secret");
        String a = c.encrypt("123456");
        String b = c.encrypt("123456");
        assertThat(a).startsWith("v1:").doesNotContain("123456").isNotEqualTo(b);
        assertThat(c.decrypt(a)).isEqualTo("123456");
        assertThat(c.decrypt(b)).isEqualTo("123456");
    }

    @Test
    @DisplayName("another secret cannot read it; an altered value reads as absent")
    void otherKeyOrTamper() {
        String sealed = new HandoverCodeCipher("secret-one").encrypt("123456");
        assertThat(new HandoverCodeCipher("secret-two").decrypt(sealed)).isNull();
        char last = sealed.charAt(sealed.length() - 2);
        String altered = sealed.substring(0, sealed.length() - 2) + (last == 'A' ? 'B' : 'A') + sealed.charAt(sealed.length() - 1);
        assertThat(new HandoverCodeCipher("secret-one").decrypt(altered)).isNull();
        assertThat(new HandoverCodeCipher("secret-one").decrypt(null)).isNull();
        assertThat(new HandoverCodeCipher("secret-one").decrypt("garbage")).isNull();
    }

    @Test
    @DisplayName("no secret configured: never refuses, works with a development key")
    void noSecret() {
        HandoverCodeCipher c = new HandoverCodeCipher("");
        assertThat(c.decrypt(c.encrypt("000042"))).isEqualTo("000042");
        assertThat(HandoverCodeCipher.derive("x")).hasSize(32);
    }
}
