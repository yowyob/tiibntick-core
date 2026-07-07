package com.yowyob.tiibntick.core.billing.report.application.service;

import com.yowyob.tiibntick.core.billing.report.application.port.out.InvoiceReportProjectionRepository;
import com.yowyob.tiibntick.core.billing.report.domain.model.InvoiceReportEntry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Application service that persists invoice projection entries consumed from Kafka.
 * Called by the Kafka adapter when InvoiceGenerated/InvoicePaid/InvoiceCancelled events arrive.
 *
 * @author MANFOUO Braun
 */
@Service
public class InvoiceProjectionService {

    private final InvoiceReportProjectionRepository projectionRepository;

    public InvoiceProjectionService(InvoiceReportProjectionRepository projectionRepository) {
        this.projectionRepository = projectionRepository;
    }

    public Mono<Void> project(InvoiceReportEntry entry) {
        return projectionRepository.upsert(entry);
    }
}
