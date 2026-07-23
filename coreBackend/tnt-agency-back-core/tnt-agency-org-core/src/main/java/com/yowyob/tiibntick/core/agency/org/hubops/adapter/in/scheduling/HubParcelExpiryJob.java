package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.scheduling;

import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelExpiryService;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Marks hub parcels past {@code withdrawal_deadline} as EXPIRED.
 * When {@code tnt.agency.hub.expiry-job.tenant-id} is set, scopes to that tenant;
 * otherwise sweeps all tenants.
 */
@Component
@ConditionalOnProperty(name = "tnt.agency.hub.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class HubParcelExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HubParcelExpiryJob.class);

    private final HubParcelExpiryService expiryService;
    private final UUID scheduledTenantId;

    public HubParcelExpiryJob(
            HubParcelExpiryService expiryService,
            @Value("${tnt.agency.hub.expiry-job.tenant-id:}") String tenantId) {
        this.expiryService = expiryService;
        this.scheduledTenantId = tenantId != null && !tenantId.isBlank()
                ? UUID.fromString(tenantId.trim()) : null;
    }

    @Scheduled(cron = "${tnt.agency.hub.expiry-job.cron:0 0 * * * *}")
    @SchedulerLock(name = "agency-hub-parcel-expiry", lockAtMostFor = "PT55M", lockAtLeastFor = "PT1M")
    public void run() {
        LockAssert.assertLocked();
        Mono<Integer> work = scheduledTenantId != null
                ? expiryService.processExpired(scheduledTenantId)
                : expiryService.processExpiredAllTenants();
        work.doOnSuccess(count -> {
                    if (count != null && count > 0) {
                        log.info("HubParcelExpiryJob processed {} expired parcel(s)", count);
                    }
                })
                .doOnError(e -> log.warn("HubParcelExpiryJob failed: {}", e.getMessage()))
                .subscribe();
    }
}
