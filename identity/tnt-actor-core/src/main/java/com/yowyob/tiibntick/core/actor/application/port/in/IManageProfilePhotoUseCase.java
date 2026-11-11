package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.port.out.IKernelUserAccountPort;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * The profile photo of the signed-in account. It belongs to the account, not to a profile, so a client,
 * a deliverer and a relay operator profile of the same person show the same photo on every TiiBnTick
 * platform. The Kernel stores, links and serves the file; this use case only chains its endpoints.
 */
public interface IManageProfilePhotoUseCase {

    /** What the platforms need to show the photo; the bytes are fetched from {@code content...}. */
    record ProfilePhoto(UUID photoId, String contentType, long size, String analysisStatus) {
    }

    /** Uploads {@code file} to the Kernel and makes it the account photo (replacing the previous one). */
    Mono<ProfilePhoto> upload(FilePart file);

    /** The current photo of the signed-in account; empty when there is none. */
    Mono<ProfilePhoto> current();

    /** Removes the photo of the signed-in account. */
    Mono<Void> remove();

    /** The bytes of the signed-in account's photo; empty when there is none. */
    Mono<IKernelUserAccountPort.FileContent> contentOfCurrent();

    /** The bytes of another actor's photo; empty when that actor has none. */
    Mono<IKernelUserAccountPort.FileContent> contentOfActor(UUID actorId);
}
