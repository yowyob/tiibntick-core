package com.yowyob.tiibntick.core.administration.application.service;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.administration.application.port.in.ResolveDisputeFilingWindowUseCase;
import com.yowyob.tiibntick.core.administration.application.port.out.TntPlatformOptionsRepository;
import com.yowyob.tiibntick.core.administration.domain.model.TntPlatformOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** {@link ResolveDisputeFilingWindowUseCase} over the platform options (lot L4). */
@Service
public class DisputeFilingWindowService implements ResolveDisputeFilingWindowUseCase {

    private final TntPlatformOptionsRepository options;

    public DisputeFilingWindowService(TntPlatformOptionsRepository options) {
        this.options = options;
    }

    @Override
    public Mono<Integer> filingWindowDays(UUID tenantId) {
        Mono<TntPlatformOptions> system = Mono.defer(() -> options.findByTenantId(SystemTenant.id()));
        Mono<TntPlatformOptions> own = tenantId == null || tenantId.equals(SystemTenant.id()) ? system
                : options.findByTenantId(tenantId).switchIfEmpty(system);
        return own.map(TntPlatformOptions::getDisputeFilingWindowDays)
                .filter(days -> days >= 0)
                .defaultIfEmpty(DEFAULT_DAYS);
    }
}
