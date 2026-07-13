package com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.scheduling;

import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
    public void run() {
        if (scheduledTenantId == null) {
            log.debug("HubParcelExpiryJob skipped — no tnt.agency.hub.expiry-job.tenant-id");
            return;
        }
        expiryService.processExpired(scheduledTenantId)
                .doOnSuccess(count -> log.info("HubParcelExpiryJob processed {} expired parcel(s)", count))
                .doOnError(e -> log.warn("HubParcelExpiryJob failed: {}", e.getMessage()))
                .subscribe();
    }
}
