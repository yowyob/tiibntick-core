package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.actor.application.port.in.IManageProfilePhotoUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IManageProfilePhotoUseCase.ProfilePhoto;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelUserAccountPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * The profile photo of the account, served for every TiiBnTick platform. The Kernel does the storing;
 * these endpoints only forward to it ({@link IManageProfilePhotoUseCase}).
 */
@RestController
@RequestMapping("/api/v1/actors")
@Tag(name = "Profile photo", description = "Profile photo of the account (stored by the Kernel)")
public class ProfilePhotoController {

    public record PhotoView(UUID photoId, String contentType, long size, String analysisStatus, String contentPath) {
        static PhotoView of(ProfilePhoto p) {
            return new PhotoView(p.photoId(), p.contentType(), p.size(), p.analysisStatus(),
                    "/api/v1/actors/me/photo/content");
        }
    }

    private final IManageProfilePhotoUseCase useCase;

    public ProfilePhotoController(IManageProfilePhotoUseCase useCase) {
        this.useCase = useCase;
    }

    @PutMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set my profile photo (multipart field 'file'); replaces the previous one")
    public Mono<ApiResponse<PhotoView>> upload(@RequestPart("file") Mono<FilePart> file) {
        return file.flatMap(useCase::upload).map(p -> ApiResponse.success(PhotoView.of(p)));
    }

    @GetMapping("/me/photo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My profile photo (metadata); 404 when I have none")
    public Mono<ResponseEntity<ApiResponse<PhotoView>>> current() {
        return useCase.current()
                .map(p -> ResponseEntity.ok(ApiResponse.success(PhotoView.of(p))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("PHOTO_NOT_SET", "No profile photo for this account", null)));
    }

    @DeleteMapping("/me/photo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove my profile photo")
    public Mono<ResponseEntity<Void>> remove() {
        return useCase.remove().thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @GetMapping("/me/photo/content")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "The image of my profile photo")
    public Mono<ResponseEntity<Flux<DataBuffer>>> myContent() {
        return image(useCase.contentOfCurrent());
    }

    @GetMapping("/{actorId}/photo/content")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "The image of another actor's profile photo")
    public Mono<ResponseEntity<Flux<DataBuffer>>> actorContent(@PathVariable UUID actorId) {
        return image(useCase.contentOfActor(actorId));
    }

    private static Mono<ResponseEntity<Flux<DataBuffer>>> image(Mono<IKernelUserAccountPort.FileContent> content) {
        return content
                .map(c -> ResponseEntity.ok()
                        .contentType(c.contentType())
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .body(c.body()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
