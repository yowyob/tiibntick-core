package com.yowyob.tiibntick.core.gofreelancer.adapter.out.clients;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.KycVerificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter that calls the tnt-actor-core KYC verification endpoint.
 *
 * <p>Forwards the document as a {@code multipart/form-data} POST to
 * {@code /api/v1/kyc/verify} (base URL configured in {@code kyc.verify.url}).
 *
 * <ul>
 *   <li>2xx → document accepted → returns {@code true}</li>
 *   <li>any other status → rejected/service error → returns {@code false}</li>
 *   <li>connection error → propagates as {@link IllegalStateException}</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class KycVerificationAdapter implements KycVerificationPort {

    private final WebClient kycWebClient;

    public KycVerificationAdapter(@Qualifier("kycWebClient") WebClient kycWebClient) {
        this.kycWebClient = kycWebClient;
    }

    @Override
    public Mono<Boolean> verifyDocument(FilePart document, String authorization) {
        log.debug("Sending document '{}' to KYC verification service", document.filename());

        return readBytes(document)
                .flatMap(bytes -> {
                    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
                    bodyBuilder.part("file", bytes)
                            .filename(document.filename())
                            .contentType(contentType(document));

                    WebClient.RequestBodySpec requestSpec = kycWebClient
                            .post()
                            .contentType(MediaType.MULTIPART_FORM_DATA);

                    if (authorization != null && !authorization.isBlank()) {
                        requestSpec = requestSpec.header("Authorization", authorization);
                    }

                    return requestSpec
                            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                            .exchangeToMono(response -> {
                                if (response.statusCode().is2xxSuccessful()) {
                                    log.info("KYC verification passed for '{}'", document.filename());
                                    return response.bodyToMono(Void.class).thenReturn(true);
                                } else {
                                    log.warn("KYC verification rejected '{}' — HTTP {}",
                                            document.filename(), response.statusCode().value());
                                    return response.bodyToMono(Void.class).thenReturn(false);
                                }
                            });
                })
                .onErrorResume(IllegalStateException.class, Mono::error)
                .onErrorResume(ex -> {
                    log.error("KYC service unreachable for '{}': {}", document.filename(), ex.getMessage());
                    return Mono.error(new IllegalStateException(
                            "KYC verification service is unavailable. Registration cannot proceed.", ex));
                });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Reads all DataBuffer chunks from the FilePart into a single byte[]. */
    private Mono<byte[]> readBytes(FilePart filePart) {
        return filePart.content()
                .map(DataBuffer::asByteBuffer)
                .collectList()
                .map(buffers -> {
                    int total = buffers.stream().mapToInt(java.nio.ByteBuffer::remaining).sum();
                    byte[] result = new byte[total];
                    int offset = 0;
                    for (java.nio.ByteBuffer buf : buffers) {
                        int len = buf.remaining();
                        buf.get(result, offset, len);
                        offset += len;
                    }
                    return result;
                });
    }

    private MediaType contentType(FilePart filePart) {
        MediaType ct = filePart.headers().getContentType();
        return ct != null ? ct : MediaType.APPLICATION_OCTET_STREAM;
    }
}
