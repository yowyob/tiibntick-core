package com.yowyob.tiibntick.core.media.domain;

/**
 * Document type used to select the correct JasperReports template
 * during PDF rendering in {@link com.yowyob.tiibntick.core.media.service.ManifestPdfService}
 * and related services.
 *
 * @author MANFOUO Braun
 */
public enum PDFType {
    INVOICE,
    MANIFEST,
    DELIVERY_RECEIPT,
    HUB_DEPOSIT_RECEIPT,
    COMMISSION_STATEMENT,
    CONTRACT,
    MONTHLY_REPORT,
    HUB_DAILY_REPORT
}
