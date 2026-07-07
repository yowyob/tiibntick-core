package com.yowyob.tiibntick.core.billing.report.adapter.out.export;

import com.yowyob.tiibntick.core.billing.report.application.port.out.ReportExportPort;
import com.yowyob.tiibntick.core.billing.report.domain.model.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Adapter: exports billing reports to CSV using Apache Commons CSV.
 *
 * @author MANFOUO Braun
 */
@Component
public class CsvReportExportAdapter implements ReportExportPort {

    @Override
    public Mono<byte[]> exportRevenueToCsv(RevenueReport report) {
        return Mono.fromCallable(() -> {
            StringWriter sw = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                    .setHeader("Period", "TotalInvoices", "Paid", "Cancelled", "Overdue",
                            "GrossRevenue", "CollectedRevenue", "CancelledRevenue",
                            "TotalTax", "NetRevenue", "CollectionRate%", "Currency")
                    .build())) {
                printer.printRecord(
                        report.getPeriod().toString(),
                        report.getTotalInvoicesGenerated(),
                        report.getTotalInvoicesPaid(),
                        report.getTotalInvoicesCancelled(),
                        report.getTotalInvoicesOverdue(),
                        report.getGrossRevenue().amount(),
                        report.getCollectedRevenue().amount(),
                        report.getCancelledRevenue().amount(),
                        report.getTotalTaxCollected().amount(),
                        report.getNetRevenue().amount(),
                        String.format("%.2f", report.collectionRatePercent()),
                        report.getGrossRevenue().currency());

                // Country breakdowns
                if (!report.getCountryBreakdowns().isEmpty()) {
                    printer.println();
                    printer.printRecord("Country", "InvoiceCount", "Revenue", "Tax");
                    for (RevenueReport.CountryRevenueBreakdown bd : report.getCountryBreakdowns()) {
                        printer.printRecord(bd.countryCode(), bd.invoiceCount(),
                                bd.revenue().amount(), bd.taxAmount().amount());
                    }
                }
            }
            return sw.toString().getBytes(StandardCharsets.UTF_8);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<byte[]> exportCommissionsToCsv(CommissionSummary summary) {
        return Mono.fromCallable(() -> {
            StringWriter sw = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                    .setHeader("Period", "TotalDeliveries", "CommissionsEarned",
                            "CommissionsPaid", "CommissionsPending", "AvgRate%", "Currency")
                    .build())) {
                printer.printRecord(
                        summary.getPeriod().toString(),
                        summary.getTotalDeliveries(),
                        summary.getTotalCommissionsEarned().amount(),
                        summary.getTotalCommissionsPaid().amount(),
                        summary.getTotalCommissionsPending().amount(),
                        String.format("%.2f", summary.getAverageCommissionRate()),
                        summary.getTotalCommissionsEarned().currency());

                if (!summary.getActorBreakdowns().isEmpty()) {
                    printer.println();
                    printer.printRecord("ActorId", "ActorName", "ActorType",
                            "DeliveryCount", "CommissionAmount", "Rate%");
                    for (CommissionSummary.ActorCommission ac : summary.getActorBreakdowns()) {
                        printer.printRecord(ac.actorId(), ac.actorName(), ac.actorType(),
                                ac.deliveryCount(), ac.commissionAmount().amount(),
                                String.format("%.2f", ac.commissionRate()));
                    }
                }
            }
            return sw.toString().getBytes(StandardCharsets.UTF_8);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<byte[]> exportMarginToCsv(MarginReport report) {
        return Mono.fromCallable(() -> {
            StringWriter sw = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                    .setHeader("Period", "TotalRevenue", "TotalCosts", "GrossMargin",
                            "GrossMargin%", "PlatformFees", "NetMargin", "NetMargin%", "Currency")
                    .build())) {
                printer.printRecord(
                        report.getPeriod().toString(),
                        report.getTotalRevenue().amount(),
                        report.getTotalCosts().amount(),
                        report.getGrossMargin().amount(),
                        String.format("%.2f", report.getGrossMarginPercent()),
                        report.getTotalPlatformFees().amount(),
                        report.getNetMargin().amount(),
                        String.format("%.2f", report.getNetMarginPercent()),
                        report.getTotalRevenue().currency());
            }
            return sw.toString().getBytes(StandardCharsets.UTF_8);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
