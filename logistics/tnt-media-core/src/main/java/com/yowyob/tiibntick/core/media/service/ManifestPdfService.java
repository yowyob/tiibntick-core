package com.yowyob.tiibntick.core.media.service;

import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import com.yowyob.tiibntick.core.media.domain.PDFRenderRequest;
import com.yowyob.tiibntick.core.media.domain.PDFType;
import com.yowyob.tiibntick.core.media.port.inbound.IGenerateManifestPdfUseCase;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import com.yowyob.tiibntick.core.media.port.outbound.IObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for PDF document generation using JasperReports.
 * <p>
 * Templates are compiled once at startup and cached in memory.
 * All blocking JasperReports operations run on the bounded elastic scheduler.
 * <p>
 * Supported document types: delivery manifest, invoice, hub deposit receipt,
 * commission statement, monthly report, hub daily report.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManifestPdfService implements IGenerateManifestPdfUseCase {

    static {
        // Disable external DTD validation to avoid network dependency during template compilation
        System.setProperty("net.sf.jasperreports.compiler.xml.validation", "false");
        // Disable external DTD resolution
        System.setProperty("javax.xml.accessExternalDTD", "");
    }

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final IObjectStorageClient storageClient;
    private final IMediaRepository mediaRepository;

    private final ConcurrentHashMap<PDFType, JasperReport> reportCache = new ConcurrentHashMap<>();

    @Override
    public Mono<String> generateAndStore(PDFRenderRequest request) {
        request.validate();
        return generateBytes(request)
                .flatMap(pdfBytes -> {
                    String bucket = MediaFile.bucketNameFor(request.getTenantId());
                    String key = buildPdfKey(request);
                    return storageClient.ensureBucketExists(bucket)
                            .then(storageClient.upload(bucket, key, pdfBytes, PDF_CONTENT_TYPE))
                            .then(persistMetadata(request, bucket, key, pdfBytes.length))
                            .thenReturn(key);
                });
    }

    @Override
    public Mono<byte[]> generateBytes(PDFRenderRequest request) {
        request.validate();
        return Mono.fromCallable(() -> renderPdf(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ── Private rendering logic ────────────────────────────────────────────────

    private byte[] renderPdf(PDFRenderRequest request) {
        try {
            JasperReport report = getCompiledReport(request.getTemplateType());
            Map<String, Object> params = new HashMap<>(request.variables());
            params.put("REPORT_LOCALE", parseLocale(request.getLocale()));

            JasperPrint print = JasperFillManager.fillReport(
                    report, params, new JRMapCollectionDataSource(buildDataSource(params)));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JasperExportManager.exportReportToPdfStream(print, out);
            return out.toByteArray();
        } catch (JRException e) {
            throw new RuntimeException("PDF rendering failed for template: "
                    + request.getTemplateType(), e);
        }
    }

    private JasperReport getCompiledReport(PDFType type) {
        return reportCache.computeIfAbsent(type, t -> {
            String templatePath = resolveTemplatePath(t);
            try (InputStream is = getClass().getResourceAsStream(templatePath)) {
                if (is == null) {
                    throw new IllegalStateException("JasperReports template not found: " + templatePath);
                }
                log.info("Compiling JasperReports template: {}", templatePath);
                return JasperCompileManager.compileReport(is);
            } catch (JRException | java.io.IOException e) {
                throw new RuntimeException("Failed to compile JasperReports template: " + templatePath, e);
            }
        });
    }

    private String resolveTemplatePath(PDFType type) {
        return switch (type) {
            case MANIFEST -> "/templates/manifest.jrxml";
            case INVOICE -> "/templates/invoice.jrxml";
            case HUB_DEPOSIT_RECEIPT -> "/templates/hub_receipt.jrxml";
            case COMMISSION_STATEMENT -> "/templates/commission_statement.jrxml";
            case MONTHLY_REPORT -> "/templates/monthly_report.jrxml";
            case HUB_DAILY_REPORT -> "/templates/hub_daily_report.jrxml";
            case DELIVERY_RECEIPT -> "/templates/delivery_receipt.jrxml";
            case CONTRACT -> "/templates/contract.jrxml";
        };
    }

    @SuppressWarnings("unchecked")
    private Collection<Map<String, ?>> buildDataSource(Map<String, Object> params) {
        Object rows = params.get("DATA_ROWS");
        if (rows instanceof Collection<?> col) {
            return (Collection<Map<String, ?>>) col;
        }
        return List.of(params);
    }

    private java.util.Locale parseLocale(String locale) {
        if (locale == null) return java.util.Locale.FRENCH;
        String[] parts = locale.split("[_-]");
        return parts.length >= 2
                ? java.util.Locale.of(parts[0], parts[1])
                : java.util.Locale.of(parts[0]);
    }

    private String buildPdfKey(PDFRenderRequest request) {
        String name = request.getOutputFileName() != null
                ? request.getOutputFileName()
                : request.getTemplateType().name().toLowerCase() + "_" + System.currentTimeMillis();
        return "documents/" + request.getTemplateType().name().toLowerCase() + "/" + name + ".pdf";
    }

    private Mono<Void> persistMetadata(PDFRenderRequest request, String bucket, String key, int size) {
        MediaType mediaType = switch (request.getTemplateType()) {
            case INVOICE -> MediaType.INVOICE_PDF;
            case MANIFEST, DELIVERY_RECEIPT -> MediaType.DELIVERY_MANIFEST;
            case HUB_DEPOSIT_RECEIPT -> MediaType.HUB_DEPOSIT_RECEIPT;
            case COMMISSION_STATEMENT -> MediaType.COMMISSION_STATEMENT;
            default -> MediaType.MONTHLY_REPORT;
        };
        MediaFile file = MediaFile.create(
                request.getTenantId(),
                null,
                mediaType,
                PDF_CONTENT_TYPE,
                key.substring(key.lastIndexOf('/') + 1),
                bucket,
                key,
                size,
                null,
                false,
                LocalDateTime.now().plusYears(5),
                Map.of("templateType", request.getTemplateType().name(), "locale", request.getLocale()));
        return mediaRepository.save(file).then();
    }
}
