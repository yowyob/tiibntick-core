package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.actor.application.port.in.IProxyKernelKycVerificationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Proxies the Kernel's {@code kyc-verification-controller} — a document-analysis
 * (OCR + validity check) endpoint distinct from {@link ActorKycController}'s own
 * submit/validate approval workflow. TiiBnTick Core performs NO document analysis of
 * its own here: it only forwards the caller's document to the Kernel and re-wraps the
 * response in the Core's standard {@link ApiResponse} envelope.
 *
 * <p>The Kernel's OpenAPI spec does not name a fixed multipart field for this endpoint —
 * {@code "file"} is TiiBnTick Core's own documented contract (required, so Spring returns
 * a 400 automatically if the caller omits it) — see {@code KernelKycVerificationAdapter}'s
 * javadoc for why.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/kyc")
@Tag(name = "KYC Verification (Kernel Proxy)", description = "Kernel kyc-verification-controller proxy — document OCR/validity analysis")
public class KycVerificationProxyController {

    private final IProxyKernelKycVerificationUseCase useCase;

    public KycVerificationProxyController(IProxyKernelKycVerificationUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Analyze an identity document via the Kernel (OCR extraction + validity check)",
            description = "Requires a single 'file' multipart part containing the identity document image/PDF to analyze.")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> verify(
            @Parameter(description = "Document to analyze", required = true) @RequestPart("file") FilePart file,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return useCase.verifyDocument(file, authorization)
                .map(result -> ResponseEntity.status(result.httpStatus()).body(result.toApiResponse()));
    }
}
