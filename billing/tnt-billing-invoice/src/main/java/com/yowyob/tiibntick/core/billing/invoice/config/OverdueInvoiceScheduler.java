package com.yowyob.tiibntick.core.billing.invoice.config;

import com.yowyob.tiibntick.core.billing.invoice.application.service.InvoiceService;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that marks invoices as OVERDUE once their due date has passed.
 * Runs every hour.
 *
 * @author MANFOUO Braun
 */
@Component
public class OverdueInvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceScheduler.class);

    private final InvoiceService invoiceService;

    public OverdueInvoiceScheduler(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Scheduled(fixedDelayString = "${tnt.billing.invoice.overdue-check-interval-ms:3600000}")
    @SchedulerLock(name = "invoice-check-overdue", lockAtMostFor = "PT55M", lockAtLeastFor = "PT1M")
    public void checkOverdueInvoices() {
        LockAssert.assertLocked();
        log.info("Running overdue invoice check...");
        invoiceService.markOverdueInvoices()
                .doOnSuccess(count -> log.info("Overdue check complete: {} invoice(s) marked", count))
                .doOnError(e -> log.error("Overdue check failed: {}", e.getMessage()))
                .subscribe();
    }
}
