package com.yowyob.tiibntick.core.tp.domain.model;

import com.yowyob.tiibntick.core.tp.domain.model.enums.KycStatus;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;
import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TntClientProfile aggregate root.
 *
 * @author MANFOUO Braun
 */
class TntClientProfileTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TP_ID = UUID.randomUUID();

    @Test
    void create_shouldInitializeWithDefaultValues() {
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, TP_ID, Set.of(TntThirdPartyRole.SENDER), "fr", "XAF");

        assertThat(profile.getId()).isNotNull();
        assertThat(profile.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(profile.getThirdPartyId()).isEqualTo(TP_ID);
        assertThat(profile.getKycStatus()).isEqualTo(KycStatus.NOT_SUBMITTED);
        assertThat(profile.getLoyaltyTier()).isEqualTo(LoyaltyTier.BRONZE);
        assertThat(profile.isActive()).isTrue();
        assertThat(profile.isPhoneMasked()).isFalse();
        assertThat(profile.getAverageRating()).isNull();
        assertThat(profile.getRatingCount()).isZero();
        assertThat(profile.getDomainEvents()).hasSize(1);
    }

    @Test
    void applyRating_shouldRecalculateAverageCorrectly() {
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, TP_ID, Set.of(TntThirdPartyRole.SENDER), "fr", "XAF");

        TntClientProfile after1 = profile.applyRating(4.0);
        assertThat(after1.getAverageRating()).isEqualTo(4.0);
        assertThat(after1.getRatingCount()).isEqualTo(1);

        TntClientProfile after2 = after1.applyRating(2.0);
        assertThat(after2.getAverageRating()).isEqualTo(3.0);
        assertThat(after2.getRatingCount()).isEqualTo(2);
    }

    @Test
    void applyRating_withInvalidScore_shouldThrow() {
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, TP_ID, Set.of(TntThirdPartyRole.SENDER), "fr", "XAF");

        assertThatThrownBy(() -> profile.applyRating(6.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.applyRating(0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignPhoneAlias_shouldMaskPhone() {
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, TP_ID, Set.of(TntThirdPartyRole.SENDER), "fr", "XAF");

        TntClientProfile masked = profile.assignPhoneAlias("+237-6TT-123-456");

        assertThat(masked.isPhoneMasked()).isTrue();
        assertThat(masked.getPhoneAlias()).isEqualTo("+237-6TT-123-456");
    }

    @Test
    void updateLoyaltyTier_shouldReflectCorrectTier() {
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, TP_ID, Set.of(TntThirdPartyRole.SENDER), "fr", "XAF");

        TntClientProfile silver = profile.updateLoyaltyTier(600);
        assertThat(silver.getLoyaltyTier()).isEqualTo(LoyaltyTier.SILVER);

        TntClientProfile gold = profile.updateLoyaltyTier(2500);
        assertThat(gold.getLoyaltyTier()).isEqualTo(LoyaltyTier.GOLD);

        TntClientProfile platinum = profile.updateLoyaltyTier(6000);
        assertThat(platinum.getLoyaltyTier()).isEqualTo(LoyaltyTier.PLATINUM);
    }

    @Test
    void updateKycStatus_toApproved_shouldSetVerified() {
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, TP_ID, Set.of(TntThirdPartyRole.DELIVERER), "fr", "XAF");

        TntClientProfile verified = profile.updateKycStatus(KycStatus.APPROVED);

        assertThat(verified.isKycVerified()).isTrue();
        assertThat(verified.getKycStatus()).isEqualTo(KycStatus.APPROVED);
    }

    @Test
    void incrementDeliveries_shouldIncreaseCountByOne() {
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, TP_ID, Set.of(TntThirdPartyRole.SENDER), "fr", "XAF");

        TntClientProfile updated = profile.incrementDeliveries().incrementDeliveries();

        assertThat(updated.getTotalDeliveries()).isEqualTo(2);
    }

    @Test
    void deactivate_shouldSetActiveFalse() {
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, TP_ID, Set.of(TntThirdPartyRole.SENDER), "fr", "XAF");

        TntClientProfile deactivated = profile.deactivate();

        assertThat(deactivated.isActive()).isFalse();
    }
}
