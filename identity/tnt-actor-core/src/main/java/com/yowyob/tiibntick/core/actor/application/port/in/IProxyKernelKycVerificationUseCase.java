package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.domain.model.KernelKycVerificationResult;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * Primary (inbound) use-case: proxies one call to the Kernel's
 * {@code kyc-verification-controller} ({@code POST /api/kyc/verify}) — a document-analysis
 * (OCR + validity check) endpoint distinct from this module's own KYC approval state
 * machine ({@link ISubmitKycUseCase}/{@link IValidateKycUseCase}).
 *
 * <p>Implemented by {@code KycVerificationGatewayService}, exposed by
 * {@code KycVerificationProxyController}.
 *
 * @author MANFOUO Braun
 */
public interface IProxyKernelKycVerificationUseCase {

    /**
     * Forwards the caller's document to the Kernel as a {@code file} multipart part
     * (content type and filename preserved) and returns the real Kernel HTTP status
     * alongside the response body.
     *
     * <p>{@code "file"} is TiiBnTick Core's own documented field name for this endpoint —
     * the Kernel's OpenAPI spec does not name a fixed multipart field for
     * {@code /api/kyc/verify}, so this is the API contract TiiBnTick Core commits to for
     * discoverability (Swagger). Confirm against a live Kernel if verification calls fail.
     *
     * @param document              the identity document to analyze — required
     * @param bearerAuthorization   the caller's raw {@code Authorization} header value, or {@code null}
     */
    Mono<KernelKycVerificationResult> verifyDocument(FilePart document, String bearerAuthorization);
}
