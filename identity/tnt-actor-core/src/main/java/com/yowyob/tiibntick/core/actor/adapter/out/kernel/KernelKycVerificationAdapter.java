package com.yowyob.tiibntick.core.actor.adapter.out.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelKycVerificationPort;
import com.yowyob.tiibntick.core.actor.domain.model.KernelKycVerificationResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls the Kernel's {@code kyc-verification-controller} ({@code POST /api/kyc/verify})
 * via the shared {@code kernelWebClient} bean (defined once in {@code tnt-bootstrap}'s
 * {@code KernelBridgeConfig}).
 *
 * <p>Forwards the caller's document as a {@code file} multipart part (content type and
 * filename preserved). {@code "file"} is TiiBnTick Core's own documented field name for
 * this endpoint — the Kernel's OpenAPI spec does not name a fixed multipart field for
 * {@code /api/kyc/verify} (it resolves to a bare {@code object}). Confirm against a live
 * Kernel if verification calls fail with an unexpected 4xx.
 *
 * <p>Never injects a Kernel Spring bean/type — only the generic {@link WebClient} (see
 * root {@code CLAUDE.md}: Kernel is HTTP-only).
 *
 * @author MANFOUO Braun
 */
@Component("actorCoreKernelKycVerificationAdapter")
public class KernelKycVerificationAdapter implements IKernelKycVerificationPort {

    private final WebClient kernelWebClient;

    public KernelKycVerificationAdapter(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    @Override
    public Mono<KernelKycVerificationResult> verifyDocument(FilePart document, String bearerAuthorization) {
        MediaType contentType = document.headers().getContentType() != null
                ? document.headers().getContentType() : MediaType.APPLICATION_OCTET_STREAM;
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.asyncPart("file", document.content(), DataBuffer.class)
                .contentType(contentType)
                .filename(document.filename());

        WebClient.RequestBodySpec spec = kernelWebClient.post().uri("/api/kyc/verify");
        if (bearerAuthorization != null && !bearerAuthorization.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, bearerAuthorization);
        }

        return spec.body(BodyInserters.fromMultipartData(builder.build()))
                .exchangeToMono(response -> {
                    boolean success = response.statusCode().is2xxSuccessful();
                    int status = response.statusCode().value();
                    return response.bodyToMono(JsonNode.class)
                            .defaultIfEmpty(NullNode.getInstance())
                            .map(json -> success
                                    ? new KernelKycVerificationResult(status, true, json, null, null)
                                    : new KernelKycVerificationResult(status, false, null,
                                            json.path("errorCode").asText(null),
                                            json.path("message").asText(null)))
                            .onErrorResume(ex -> Mono.just(new KernelKycVerificationResult(
                                    status, false, null,
                                    "KERNEL_HTTP_" + status,
                                    "Kernel returned a non-JSON or unexpected response body")));
                });
    }
}
