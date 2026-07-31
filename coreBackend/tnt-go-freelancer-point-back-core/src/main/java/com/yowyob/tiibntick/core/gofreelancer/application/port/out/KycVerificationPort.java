package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * Outbound port for KYC document verification.
 * Delegates to the tnt-actor-core KYC verification endpoint (POST /api/v1/kyc/verify)
 * which proxies the Kernel's OCR + validity analysis service.
 *
 * <p>Used during freelancer and relay-point registration to validate identity
 * documents before persisting the new actor.
 */
public interface KycVerificationPort {

    /**
     * Sends the given document to the KYC verification service.
     *
     * @param document      the identity document file (CNI, NUI, passport…)
     * @param authorization the caller's raw {@code Authorization} header value (Bearer token)
     * @return {@code true} if the document is valid and accepted, {@code false} otherwise
     */
    Mono<Boolean> verifyDocument(FilePart document, String authorization);
}
