package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IProxyKernelKycVerificationUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelKycVerificationPort;
import com.yowyob.tiibntick.core.actor.domain.model.KernelKycVerificationResult;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implements {@link IProxyKernelKycVerificationUseCase} — contains NO document-analysis
 * logic itself, all OCR/validity checking stays on the Kernel. This service is a thin
 * pass-through to {@link IKernelKycVerificationPort}.
 *
 * @author MANFOUO Braun
 */
@Service
public class KycVerificationGatewayService implements IProxyKernelKycVerificationUseCase {

    private final IKernelKycVerificationPort kernelKycVerificationPort;

    public KycVerificationGatewayService(IKernelKycVerificationPort kernelKycVerificationPort) {
        this.kernelKycVerificationPort = kernelKycVerificationPort;
    }

    @Override
    public Mono<KernelKycVerificationResult> verifyDocument(FilePart document, String bearerAuthorization) {
        return kernelKycVerificationPort.verifyDocument(document, bearerAuthorization);
    }
}
