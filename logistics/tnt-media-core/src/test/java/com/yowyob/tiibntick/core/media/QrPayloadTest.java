package com.yowyob.tiibntick.core.media;

import com.yowyob.tiibntick.core.media.domain.QrPayload;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link QrPayload} value object.
 *
 * @author MANFOUO Braun
 */
class QrPayloadTest {

    @Test
    void toQRCodeString_shouldFailWhenUnsigned() {
        QrPayload payload = QrPayload.of("TNT-001", UUID.randomUUID(), null, "tenant-1");
        assertThatThrownBy(payload::toQRCodeString)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signed");
    }

    @Test
    void toQRCodeString_shouldProduceParseableString() {
        UUID missionId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        QrPayload payload = QrPayload.of("TNT-CMR-2026-000099", missionId, packageId, "tenant-abc")
                .withSignature("test-hmac-signature");

        String encoded = payload.toQRCodeString();
        assertThat(encoded).contains("TNT:1:TNT-CMR-2026-000099");

        QrPayload parsed = QrPayload.parse(encoded);
        assertThat(parsed.getTrackingCode()).isEqualTo("TNT-CMR-2026-000099");
        assertThat(parsed.getMissionId()).isEqualTo(missionId);
        assertThat(parsed.getPackageId()).isEqualTo(packageId);
        assertThat(parsed.getTenantId()).isEqualTo("tenant-abc");
        assertThat(parsed.getHmacSignature()).isEqualTo("test-hmac-signature");
    }

    @Test
    void parse_shouldHandleNullPackageId() {
        QrPayload payload = QrPayload.of("TNT-001", UUID.randomUUID(), null, "t1")
                .withSignature("sig");

        QrPayload parsed = QrPayload.parse(payload.toQRCodeString());
        assertThat(parsed.getPackageId()).isNull();
    }

    @Test
    void signingMessage_shouldNotIncludeSignature() {
        QrPayload unsigned = QrPayload.of("CODE-1", UUID.randomUUID(), null, "tenant");
        String msg = unsigned.signingMessage();
        assertThat(msg).doesNotContain("null:null");
        assertThat(msg).startsWith("TNT:1:CODE-1:");
    }

    @Test
    void parse_shouldRejectInvalidFormat() {
        assertThatThrownBy(() -> QrPayload.parse("not-a-valid-qr-payload"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
