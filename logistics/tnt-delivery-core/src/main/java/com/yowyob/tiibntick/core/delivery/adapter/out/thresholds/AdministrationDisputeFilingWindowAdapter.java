package com.yowyob.tiibntick.core.delivery.adapter.out.thresholds;

import com.yowyob.tiibntick.core.administration.application.port.in.ResolveDisputeFilingWindowUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.out.DisputeFilingWindowPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/** {@link DisputeFilingWindowPort} read from the administration platform options (lot L4). */
@Slf4j
@Component
public class AdministrationDisputeFilingWindowAdapter implements DisputeFilingWindowPort {

    private final ResolveDisputeFilingWindowUseCase windows;

    public AdministrationDisputeFilingWindowAdapter(ResolveDisputeFilingWindowUseCase windows) {
        this.windows = windows;
    }

    @Override
    public Mono<Duration> filingWindow(UUID tenantId) {
        return windows.filingWindowDays(tenantId)
                .map(days -> Duration.ofDays(days))
                .defaultIfEmpty(DEFAULT)
                .onErrorResume(e -> {
                    log.warn("Dispute filing window of tenant {} unreadable, default {} used: {}", tenantId, DEFAULT,
                            e.getMessage());
                    return Mono.just(DEFAULT);
                });
    }
}
