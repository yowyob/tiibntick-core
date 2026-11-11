package com.yowyob.tiibntick.core.actor.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelUserAccountPort;
import com.yowyob.tiibntick.core.actor.domain.model.KernelStoredFile;
import com.yowyob.tiibntick.core.actor.domain.model.KernelUserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Kernel {@code user-controller} / {@code file-controller} adapter. Uses {@code kernelUserWebClient}: the
 * caller's bearer is propagated, so "me" is the signed-in person.
 */
@Component("actorCoreKernelUserAccountAdapter")
public class KernelUserAccountAdapter implements IKernelUserAccountPort {

    private static final Logger log = LoggerFactory.getLogger(KernelUserAccountAdapter.class);

    private final WebClient kernel;

    public KernelUserAccountAdapter(@Qualifier("kernelUserWebClient") WebClient kernelUserWebClient) {
        this.kernel = kernelUserWebClient;
    }

    @Override
    public Mono<KernelUserAccount> findCurrent() {
        return KernelResponses.unwrapObject(kernel.get().uri("/api/users/me").retrieve(),
                KernelUserAccount.class, log, "findCurrent user account");
    }

    @Override
    public Mono<KernelStoredFile> uploadFile(FilePart file, String documentType, boolean publicFile) {
        MediaType type = file.headers().getContentType() != null
                ? file.headers().getContentType() : MediaType.APPLICATION_OCTET_STREAM;
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.asyncPart("file", file.content(), DataBuffer.class).contentType(type).filename(file.filename());
        var response = kernel.post()
                .uri(u -> u.path("/api/files")
                        .queryParamIfPresent("documentType", Optional.ofNullable(documentType))
                        .queryParam("public", publicFile)
                        .build())
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve();
        return KernelResponses.unwrapObjectOrPropagate(response, KernelStoredFile.class);
    }

    @Override
    public Mono<KernelUserAccount> setAvatar(UUID fileId) {
        Map<String, Object> body = new HashMap<>();
        body.put("avatarId", fileId);
        return KernelResponses.unwrapObjectOrPropagate(
                kernel.put().uri("/api/users/me/avatar").bodyValue(body).retrieve(), KernelUserAccount.class);
    }

    @Override
    public Mono<Void> setActorPhoto(UUID fileId) {
        Map<String, Object> body = new HashMap<>();
        body.put("photoId", fileId);
        return kernel.put().uri("/api/actors/me/identity").bodyValue(body).retrieve()
                .toBodilessEntity().then();
    }

    @Override
    public Mono<KernelStoredFile> fileMetadata(UUID fileId) {
        return KernelResponses.unwrapObject(kernel.get().uri("/api/files/{id}/metadata", fileId).retrieve(),
                KernelStoredFile.class, log, "file metadata " + fileId);
    }

    @Override
    public Mono<FileContent> fileContent(UUID fileId) {
        // toEntityFlux hands back the headers at once and keeps the body streaming; a plain exchange would
        // release the connection before the caller reads the bytes.
        return kernel.get().uri("/api/files/{id}/content", fileId).retrieve()
                .toEntityFlux(DataBuffer.class)
                .map(entity -> new FileContent(
                        entity.getHeaders().getContentType() != null
                                ? entity.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM,
                        entity.getBody()))
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}
