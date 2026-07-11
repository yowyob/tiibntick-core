package com.yowyob.tiibntick.core.actor.application.port.out;

import com.yowyob.tiibntick.core.actor.domain.model.KernelKycVerificationResult;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * Secondary (outbound) port: calls the Kernel's {@code kyc-verification-controller}
 * ({@code POST /api/kyc/verify}) — see {@code docs/kernel-api/endpoints.md} for the
 * full catalogue.
 *
 * <p>Implemented by {@code KernelKycVerificationAdapter} using the shared
 * {@code kernelWebClient} bean — never a Kernel Spring bean/type (see root
 * {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
public interface IKernelKycVerificationPort {

    Mono<KernelKycVerificationResult> verifyDocument(FilePart document, String bearerAuthorization);
}
