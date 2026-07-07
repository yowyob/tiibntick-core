package com.yowyob.tiibntick.core.media.port.inbound;

import com.yowyob.tiibntick.core.media.domain.PDFRenderRequest;
import reactor.core.publisher.Mono;

/**
 * Inbound port — PDF manifest and document generation use case.
 * <p>
 * Implementations render PDFs using JasperReports compiled templates (.jrxml → .jasper)
 * and return the result as a byte array or store it directly in MinIO.
 *
 * @author MANFOUO Braun
 */
public interface IGenerateManifestPdfUseCase {

    /**
     * Renders a PDF document from the given request and stores it in MinIO.
     *
     * @param request the rendering request with template type, locale, and variables
     * @return MinIO storage key of the generated PDF
     */
    Mono<String> generateAndStore(PDFRenderRequest request);

    /**
     * Renders a PDF document from the given request and returns raw bytes.
     * No storage is performed — useful for streaming responses.
     *
     * @param request the rendering request
     * @return raw PDF bytes
     */
    Mono<byte[]> generateBytes(PDFRenderRequest request);
}
