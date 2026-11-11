package com.yowyob.tiibntick.core.actor.application.port.out;

import com.yowyob.tiibntick.core.actor.domain.model.KernelStoredFile;
import com.yowyob.tiibntick.core.actor.domain.model.KernelUserAccount;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * The Kernel's account and file endpoints, called on behalf of the signed-in person (bearer propagated).
 * The profile photo is entirely the Kernel's: it stores the file ({@code POST /api/files}), links it to the
 * account ({@code PUT /api/users/me/avatar}) and serves it back. TiiBnTick keeps nothing of its own.
 */
public interface IKernelUserAccountPort {

    /** {@code GET /api/users/me}. */
    Mono<KernelUserAccount> findCurrent();

    /** {@code POST /api/files} (multipart). */
    Mono<KernelStoredFile> uploadFile(FilePart file, String documentType, boolean publicFile);

    /** {@code PUT /api/users/me/avatar}; a {@code null} id removes the photo. */
    Mono<KernelUserAccount> setAvatar(UUID fileId);

    /** {@code PUT /api/actors/me/identity} with {@code photoId}, so the photo is also readable on the Kernel actor. */
    Mono<Void> setActorPhoto(UUID fileId);

    /** {@code GET /api/files/{fileId}/metadata}. */
    Mono<KernelStoredFile> fileMetadata(UUID fileId);

    /** {@code GET /api/files/{fileId}/content}. */
    Mono<FileContent> fileContent(UUID fileId);

    /** The bytes of a Kernel file as they stream in, with the type the Kernel declared. */
    record FileContent(MediaType contentType, Flux<DataBuffer> body) {
    }
}
