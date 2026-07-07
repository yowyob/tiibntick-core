package com.yowyob.tiibntick.core.administration.domain;

import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TntPlatformOptions domain model.
 * Author: MANFOUO Braun
 */
class TntPlatformOptionsTest {

    @Test
    void should_create_defaults_with_blockchain_enabled_and_xaf_currency() {
        UUID tenantId = UUID.randomUUID();
        TntPlatformOptions opts = TntPlatformOptions.defaults(tenantId);

        assertThat(opts.getTenantId()).isEqualTo(tenantId);
        assertThat(opts.isBlockchainEnabled()).isTrue();
        assertThat(opts.getDefaultCurrency()).isEqualTo("XAF");
        assertThat(opts.getTvaRate()).isEqualByComparingTo(new BigDecimal("19.25"));
        assertThat(opts.isFreelancerModeEnabled()).isTrue();
        assertThat(opts.isRequireFreelancerApproval()).isTrue();
        assertThat(opts.isPointRelaisModeEnabled()).isTrue();
        assertThat(opts.isAnnouncementMarketplaceEnabled()).isTrue();
        assertThat(opts.getBlockchainNetwork()).isEqualTo("PUBLIC_LITE");
        assertThat(opts.getDisputeFilingWindowDays()).isEqualTo(7);
    }

    @Test
    void should_update_blockchain_settings() {
        TntPlatformOptions opts = TntPlatformOptions.defaults(UUID.randomUUID());
        TntPlatformOptions updated = opts.withBlockchain(false, "PRIVATE");

        assertThat(updated.isBlockchainEnabled()).isFalse();
        assertThat(updated.getBlockchainNetwork()).isEqualTo("PRIVATE");
    }

    @Test
    void should_update_freelancer_mode() {
        TntPlatformOptions opts = TntPlatformOptions.defaults(UUID.randomUUID());
        TntPlatformOptions updated = opts.withFreelancerMode(false, false, 5);

        assertThat(updated.isFreelancerModeEnabled()).isFalse();
        assertThat(updated.isRequireFreelancerApproval()).isFalse();
        assertThat(updated.getMaxFreelancerConcurrentMissions()).isEqualTo(5);
    }

    @Test
    void defaults_should_have_non_null_id_and_timestamps() {
        TntPlatformOptions opts = TntPlatformOptions.defaults(UUID.randomUUID());
        assertThat(opts.getId()).isNotNull();
        assertThat(opts.getCreatedAt()).isNotNull();
        assertThat(opts.getUpdatedAt()).isNotNull();
    }
}
