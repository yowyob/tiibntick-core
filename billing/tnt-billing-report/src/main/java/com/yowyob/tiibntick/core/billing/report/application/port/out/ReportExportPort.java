package com.yowyob.tiibntick.core.billing.report.application.port.out;

import com.yowyob.tiibntick.core.billing.report.domain.model.*;
import reactor.core.publisher.Mono;

/**
 * Output port: exports billing reports to CSV format.
 *
 * @author MANFOUO Braun
 */
public interface ReportExportPort {

    /**
     * Exports a RevenueReport to CSV bytes.
     *
     * @param report the revenue report to export
     * @return byte array containing the UTF-8 CSV content
     */
    Mono<byte[]> exportRevenueToCsv(RevenueReport report);

    /**
     * Exports a CommissionSummary to CSV bytes.
     *
     * @param summary the commission summary to export
     * @return byte array containing the UTF-8 CSV content
     */
    Mono<byte[]> exportCommissionsToCsv(CommissionSummary summary);

    /**
     * Exports a MarginReport to CSV bytes.
     *
     * @param report the margin report to export
     * @return byte array containing the UTF-8 CSV content
     */
    Mono<byte[]> exportMarginToCsv(MarginReport report);
}
