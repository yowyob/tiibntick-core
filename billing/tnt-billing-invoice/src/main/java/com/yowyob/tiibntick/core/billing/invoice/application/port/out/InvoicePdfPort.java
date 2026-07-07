package com.yowyob.tiibntick.core.billing.invoice.application.port.out;

import com.yowyob.tiibntick.core.billing.invoice.domain.model.Invoice;
import reactor.core.publisher.Mono;

/**
 * Output port: requests PDF generation via tnt-media-core.
 * Returns the MinIO storage key of the generated PDF.
 *
 * @author MANFOUO Braun
 */
public interface InvoicePdfPort {

    /**
     * Generates a PDF for the given invoice and stores it in MinIO.
     *
     * @param invoice the invoice to render as PDF
     * @return the MinIO storage key (e.g., "invoices/TNT-FACT-AGY001-2026-000042.pdf")
     */
    Mono<String> generateAndStore(Invoice invoice);

    /**
     * Returns a pre-signed download URL for an already-generated PDF.
     *
     * @param storageKey the MinIO key
     * @param expirySeconds URL validity in seconds
     * @return pre-signed URL string
     */
    Mono<String> getDownloadUrl(String storageKey, int expirySeconds);
}
