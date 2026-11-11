package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelUserAccountPort;
import com.yowyob.tiibntick.core.actor.domain.model.KernelActorDto;
import com.yowyob.tiibntick.core.actor.domain.model.KernelStoredFile;
import com.yowyob.tiibntick.core.actor.domain.model.KernelUserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfilePhotoServiceTest {

    private final IKernelUserAccountPort kernel = mock(IKernelUserAccountPort.class);
    private final IKernelActorPort actors = mock(IKernelActorPort.class);
    private final ProfilePhotoService service = new ProfilePhotoService(kernel, actors);
    private final UUID fileId = UUID.randomUUID();

    private static KernelUserAccount account(UUID avatar) {
        return new KernelUserAccount(UUID.randomUUID(), UUID.randomUUID(), "jo", "Jo", "M", "jo@x.cm", null, avatar);
    }

    @Test
    void uploadStoresTheFileThenLinksItToTheAccountAndTheActor() {
        FilePart part = mock(FilePart.class);
        when(kernel.uploadFile(part, "PROFILE_PHOTO", true))
                .thenReturn(Mono.just(new KernelStoredFile(fileId, "me.png", "image/png", 1234, "PROFILE_PHOTO", "CLEAN", null, "PUBLIC")));
        when(kernel.setAvatar(fileId)).thenReturn(Mono.just(account(fileId)));
        when(kernel.setActorPhoto(fileId)).thenReturn(Mono.empty());

        StepVerifier.create(service.upload(part))
                .assertNext(p -> {
                    assertThat(p.photoId()).isEqualTo(fileId);
                    assertThat(p.contentType()).isEqualTo("image/png");
                    assertThat(p.analysisStatus()).isEqualTo("CLEAN");
                })
                .verifyComplete();
        verify(kernel).setAvatar(fileId);
        verify(kernel).setActorPhoto(fileId);
    }

    @Test
    void aFailedMirrorOnTheActorDoesNotFailTheUpload() {
        FilePart part = mock(FilePart.class);
        when(kernel.uploadFile(any(), any(), eq(true)))
                .thenReturn(Mono.just(new KernelStoredFile(fileId, "me.png", "image/png", 1, null, null, null, null)));
        when(kernel.setAvatar(fileId)).thenReturn(Mono.just(account(fileId)));
        when(kernel.setActorPhoto(fileId)).thenReturn(Mono.error(new IllegalStateException("kernel 500")));
        StepVerifier.create(service.upload(part)).expectNextCount(1).verifyComplete();
    }

    @Test
    void aFailedUploadLeavesTheAccountUntouched() {
        when(kernel.uploadFile(any(), any(), eq(true))).thenReturn(Mono.error(new IllegalStateException("scan refused")));
        StepVerifier.create(service.upload(mock(FilePart.class))).expectError(IllegalStateException.class).verify();
        verify(kernel, never()).setAvatar(any());
    }

    @Test
    void currentIsEmptyWithoutAvatarAndDescribedWithOne() {
        when(kernel.findCurrent()).thenReturn(Mono.just(account(null)));
        StepVerifier.create(service.current()).verifyComplete();

        when(kernel.findCurrent()).thenReturn(Mono.just(account(fileId)));
        when(kernel.fileMetadata(fileId)).thenReturn(Mono.just(
                new KernelStoredFile(fileId, "me.png", "image/png", 10, null, "CLEAN", null, null)));
        StepVerifier.create(service.current()).assertNext(p -> assertThat(p.photoId()).isEqualTo(fileId)).verifyComplete();
    }

    @Test
    void removeClearsTheAccountAvatarAndTheActorPhoto() {
        when(kernel.setAvatar(null)).thenReturn(Mono.just(account(null)));
        when(kernel.setActorPhoto(null)).thenReturn(Mono.empty());
        StepVerifier.create(service.remove()).verifyComplete();
        verify(kernel).setAvatar(null);
        verify(kernel).setActorPhoto(null);
    }

    @Test
    void contentOfAnotherActorUsesTheKernelActorPhoto() {
        UUID actor = UUID.randomUUID();
        var content = new IKernelUserAccountPort.FileContent(MediaType.IMAGE_PNG, Flux.empty());
        when(actors.findById(actor)).thenReturn(Mono.just(new KernelActorDto(actor, null, null, "A", "B", null, null,
                null, null, null, fileId)));
        when(kernel.fileContent(fileId)).thenReturn(Mono.just(content));
        StepVerifier.create(service.contentOfActor(actor)).expectNext(content).verifyComplete();

        // an actor without photo -> nothing
        UUID bare = UUID.randomUUID();
        when(actors.findById(bare)).thenReturn(Mono.just(new KernelActorDto(bare, null, null, "A", "B", null, null,
                null, null, null)));
        StepVerifier.create(service.contentOfActor(bare)).verifyComplete();
    }
}
