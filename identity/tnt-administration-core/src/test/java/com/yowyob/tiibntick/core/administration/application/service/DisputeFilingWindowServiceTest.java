package com.yowyob.tiibntick.core.administration.application.service;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.administration.application.port.out.TntPlatformOptionsRepository;
import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lot L4: the dispute filing window is the tenant's option, else the system tenant's, else 7 days. */
class DisputeFilingWindowServiceTest {

    private final TntPlatformOptionsRepository repository = mock(TntPlatformOptionsRepository.class);
    private final DisputeFilingWindowService service = new DisputeFilingWindowService(repository);

    private static TntPlatformOptions options(UUID tenant, int days) {
        return TntPlatformOptions.rehydrate(UUID.randomUUID(), tenant, true, false, "PUBLIC_LITE", true, true, 3,
                true, 72, true, 5, new BigDecimal("19.25"), "XAF", true, days, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("own window, else the system tenant's, else 7 days")
    void resolution() {
        UUID own = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(repository.findByTenantId(any())).thenReturn(Mono.empty());
        when(repository.findByTenantId(own)).thenReturn(Mono.just(options(own, 14)));
        assertThat(service.filingWindowDays(own).block()).isEqualTo(14);
        assertThat(service.filingWindowDays(other).block()).isEqualTo(7);

        when(repository.findByTenantId(SystemTenant.id())).thenReturn(Mono.just(options(SystemTenant.id(), 3)));
        assertThat(service.filingWindowDays(other).block()).isEqualTo(3);
        assertThat(service.filingWindowDays(null).block()).isEqualTo(3);
    }
}
