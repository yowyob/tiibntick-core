package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IManageProfilePhotoUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelUserAccountPort;
import com.yowyob.tiibntick.core.actor.domain.model.KernelStoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Profile photo = Kernel endpoints in a row, no rule of ours: {@code POST /api/files} stores the picture
 * (the Kernel scans and types it), {@code PUT /api/users/me/avatar} makes it the account photo,
 * {@code PUT /api/actors/me/identity} mirrors it on the Kernel actor so it can be read by other people.
 * Nothing is kept in TiiBnTick tables.
 */
@Service
public class ProfilePhotoService implements IManageProfilePhotoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProfilePhotoService.class);

    /** Kernel document type of a profile photo. */
    static final String DOCUMENT_TYPE = "PROFILE_PHOTO";

    private final IKernelUserAccountPort kernel;
    private final IKernelActorPort actors;

    public ProfilePhotoService(IKernelUserAccountPort kernel, IKernelActorPort actors) {
        this.kernel = kernel;
        this.actors = actors;
    }

    @Override
    public Mono<ProfilePhoto> upload(FilePart file) {
        // Public: a client must be able to see the photo of the deliverer who comes to their door.
        return kernel.uploadFile(file, DOCUMENT_TYPE, true)
                .switchIfEmpty(Mono.error(new IllegalStateException("The Kernel did not return the stored file")))
                .flatMap(stored -> {
                    if (stored.id() == null) {
                        return Mono.error(new IllegalStateException("The Kernel returned a file without id"));
                    }
                    return kernel.setAvatar(stored.id())
                            .then(mirrorOnActor(stored.id()))
                            .thenReturn(toPhoto(stored));
                });
    }

    @Override
    public Mono<ProfilePhoto> current() {
        return kernel.findCurrent()
                .filter(account -> account.avatarId() != null)
                .flatMap(account -> kernel.fileMetadata(account.avatarId())
                        .map(ProfilePhotoService::toPhoto)
                        .defaultIfEmpty(new ProfilePhoto(account.avatarId(), null, 0, null)));
    }

    @Override
    public Mono<Void> remove() {
        return kernel.setAvatar(null).then(mirrorOnActor(null));
    }

    @Override
    public Mono<IKernelUserAccountPort.FileContent> contentOfCurrent() {
        return kernel.findCurrent()
                .filter(account -> account.avatarId() != null)
                .flatMap(account -> kernel.fileContent(account.avatarId()));
    }

    @Override
    public Mono<IKernelUserAccountPort.FileContent> contentOfActor(UUID actorId) {
        return actors.findById(actorId)
                .flatMap(actor -> actor.photoId() == null
                        ? Mono.<IKernelUserAccountPort.FileContent>empty()
                        : kernel.fileContent(actor.photoId()));
    }

    /** Best effort: the account avatar is the source; the actor copy only makes it visible to others. */
    private Mono<Void> mirrorOnActor(UUID fileId) {
        return kernel.setActorPhoto(fileId).onErrorResume(e -> {
            log.warn("Photo {} set on the account but not mirrored on the Kernel actor: {}", fileId, e.getMessage());
            return Mono.empty();
        });
    }

    private static ProfilePhoto toPhoto(KernelStoredFile f) {
        return new ProfilePhoto(f.id(), f.contentType(), f.size(), f.analysisStatus());
    }
}
