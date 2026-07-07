package com.yowyob.tiibntick.core.media.service;

import com.yowyob.tiibntick.core.media.domain.PDFRenderRequest;
import com.yowyob.tiibntick.core.media.domain.PDFType;
import com.yowyob.tiibntick.core.media.port.inbound.IGenerateManifestPdfUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Specialized service for generating hub deposit and delivery receipts.
 * Composes {@link IGenerateManifestPdfUseCase} with receipt-specific data assembly.
 * <p>
 * Serves use cases from TiiBnTick Agency, Point, and Freelancer platforms:
 * - Hub deposit receipt (for shippers after physical parcel drop)
 * - Delivery receipt (for recipients after final delivery)
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptGenerator {

    private static final DateTimeFormatter RECEIPT_DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final IGenerateManifestPdfUseCase pdfUseCase;

    /**
     * Generates and stores a hub deposit receipt PDF.
     *
     * @param tenantId       the agency tenant
     * @param depositId      the hub deposit entity ID
     * @param trackingCode   the package tracking code
     * @param hubName        the relay hub name
     * @param shipperName    the shipper's full name
     * @param packageDesc    brief package description
     * @param depositTime    timestamp of physical deposit
     * @param locale         BCP 47 locale string
     * @return MinIO storage key of the generated receipt PDF
     */
    public Mono<String> generateHubDepositReceipt(
            String tenantId,
            String depositId,
            String trackingCode,
            String hubName,
            String shipperName,
            String packageDesc,
            LocalDateTime depositTime,
            String locale) {

        Map<String, Object> vars = new HashMap<>();
        vars.put("depositId", depositId);
        vars.put("trackingCode", trackingCode);
        vars.put("hubName", hubName);
        vars.put("shipperName", shipperName);
        vars.put("packageDescription", packageDesc);
        vars.put("depositTime", depositTime.format(RECEIPT_DATE_FMT));
        vars.put("generatedAt", LocalDateTime.now().format(RECEIPT_DATE_FMT));

        PDFRenderRequest request = PDFRenderRequest.builder()
                .templateType(PDFType.HUB_DEPOSIT_RECEIPT)
                .tenantId(tenantId)
                .locale(locale)
                .variables(vars)
                .outputFileName("receipt_deposit_" + trackingCode + "_" + depositId)
                .build();

        return pdfUseCase.generateAndStore(request);
    }

    /**
     * Generates and stores a delivery completion receipt PDF.
     *
     * @param tenantId       the agency tenant
     * @param missionId      the mission UUID
     * @param trackingCode   the package tracking code
     * @param recipientName  recipient's full name
     * @param delivererName  deliverer's full name
     * @param deliveredAt    confirmed delivery timestamp
     * @param locale         BCP 47 locale string
     * @return MinIO storage key of the generated receipt PDF
     */
    public Mono<String> generateDeliveryReceipt(
            String tenantId,
            String missionId,
            String trackingCode,
            String recipientName,
            String delivererName,
            LocalDateTime deliveredAt,
            String locale) {

        Map<String, Object> vars = new HashMap<>();
        vars.put("missionId", missionId);
        vars.put("trackingCode", trackingCode);
        vars.put("recipientName", recipientName);
        vars.put("delivererName", delivererName);
        vars.put("deliveredAt", deliveredAt.format(RECEIPT_DATE_FMT));
        vars.put("generatedAt", LocalDateTime.now().format(RECEIPT_DATE_FMT));

        PDFRenderRequest request = PDFRenderRequest.builder()
                .templateType(PDFType.DELIVERY_RECEIPT)
                .tenantId(tenantId)
                .locale(locale)
                .variables(vars)
                .outputFileName("receipt_delivery_" + trackingCode + "_" + missionId)
                .build();

        return pdfUseCase.generateAndStore(request);
    }
}
