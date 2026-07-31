package com.yowyob.tiibntick.core.media.adapter.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.media.domain.KernelStoredFile;
import com.yowyob.tiibntick.core.media.port.outbound.IKernelFileStoragePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Calls the Kernel's {@code file-controller} ({@code POST /api/files},
 * {@code GET /api/files/{fileId}/signed-url}, {@code GET /api/files/{fileId}/metadata}) via
 * the shared {@code kernelWebClient} bean (defined once in {@code tnt-bootstrap}'s
 * {@code KernelBridgeConfig}) — same pattern as {@code KernelKycVerificationAdapter}
 * (tnt-actor-core).
 *
 * <p>Used for incident/dispute evidence uploads specifically (Go-Freelancer integration,
 * 2026-07-31): the Kernel does content analysis (virus/format scanning —
 * {@code analysisStatus}/{@code analysisReason}) and access-controlled storage
 * ({@code visibility}) that TiiBnTick's own MinIO bucket does not — this is deliberately
 * separate from the MinIO-backed {@code StorageService} used by the rest of this module's
 * generic upload flow (KYC documents, delivery proofs, QR codes, PDFs), which is untouched.
 *
 * <p>Never injects a Kernel Spring bean/type — only the generic {@link WebClient} (see
 * root {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
@Component
public class KernelFileStorageAdapter implements IKernelFileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(KernelFileStorageAdapter.class);

    private final WebClient kernelWebClient;

    public KernelFileStorageAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelStoredFile> uploadFile(FilePart file, String documentType, boolean isPublic, String bearerAuthorization) {
        MediaType contentType = file.headers().getContentType() != null
                ? file.headers().getContentType() : MediaType.APPLICATION_OCTET_STREAM;
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.asyncPart("file", file.content(), DataBuffer.class)
                .contentType(contentType)
                .filename(file.filename());

        WebClient.RequestBodySpec spec = kernelWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/files")
                        .queryParamIfPresent("documentType", java.util.Optional.ofNullable(documentType))
                        .queryParam("public", isPublic)
                        .build());
        applyAuth(spec, bearerAuthorization);

        WebClient.ResponseSpec responseSpec = spec.body(BodyInserters.fromMultipartData(builder.build())).retrieve();
        return KernelResponses.unwrapObjectOrPropagate(responseSpec, KernelStoredFile.class)
                .doOnError(ex -> log.warn("Kernel file upload failed: {}", ex.getMessage()));
    }

    @Override
    public Mono<String> getSignedUrl(UUID fileId, String bearerAuthorization) {
        WebClient.RequestHeadersSpec<?> spec = kernelWebClient.get().uri("/api/files/{fileId}/signed-url", fileId);
        applyAuth(spec, bearerAuthorization);
        return KernelResponses.unwrapObjectOrPropagate(spec.retrieve(), SignedUrlResponse.class)
                .map(SignedUrlResponse::url)
                .doOnError(ex -> log.warn("Kernel signed-url request failed for fileId={}: {}", fileId, ex.getMessage()));
    }

    @Override
    public Mono<KernelStoredFile> getMetadata(UUID fileId, String bearerAuthorization) {
        WebClient.RequestHeadersSpec<?> spec = kernelWebClient.get().uri("/api/files/{fileId}/metadata", fileId);
        applyAuth(spec, bearerAuthorization);
        return KernelResponses.unwrapObjectOrPropagate(spec.retrieve(), KernelStoredFile.class)
                .doOnError(ex -> log.warn("Kernel file metadata request failed for fileId={}: {}", fileId, ex.getMessage()));
    }

    private void applyAuth(WebClient.RequestHeadersSpec<?> spec, String bearerAuthorization) {
        if (bearerAuthorization != null && !bearerAuthorization.isBlank()) {
            spec.header(HttpHeaders.AUTHORIZATION, bearerAuthorization);
        }
    }

    /** Mirrors the Kernel's {@code SignedUrlResponse} — see {@code docs/kernel-api/schemas.md}. */
    private record SignedUrlResponse(String url) {}
}
