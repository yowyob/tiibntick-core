package com.yowyob.tiibntick.core.media.domain;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Value object representing a request to render a PDF document using JasperReports.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class PDFRenderRequest {

    /** Type of PDF to render — determines which JRXML template is used. */
    private final PDFType templateType;

    /** BCP 47 locale string (e.g., "fr_CM", "en_CM") for i18n template selection. */
    @Builder.Default
    private final String locale = "fr_CM";

    /** Tenant identifier — used to load tenant-specific logo and branding. */
    private final String tenantId;

    /**
     * Key-value map of variables injected into the JasperReports parameter map.
     * Keys must exactly match the parameter names declared in the JRXML template.
     */
    @Builder.Default
    private final Map<String, Object> variables = Collections.emptyMap();

    /** Desired output file name (without extension — .pdf is appended automatically). */
    private final String outputFileName;

    public void validate() {
        Objects.requireNonNull(templateType, "PDFType must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null for PDF rendering");
    }

    public Map<String, Object> variables() {
        return Collections.unmodifiableMap(variables);
    }
}
