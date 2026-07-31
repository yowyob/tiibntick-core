package com.yowyob.tiibntick.core.media.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.media.domain.KernelStoredFile;
import com.yowyob.tiibntick.core.media.port.outbound.IKernelFileStoragePort;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST adapter for incident/dispute evidence uploads, backed by the Kernel's
 * {@code file-controller} rather than TiiBnTick's own MinIO bucket (Go-Freelancer
 * integration hardening, 2026-07-31) — see {@code KernelFileStorageAdapter}'s javadoc for
 * why this is a separate flow from {@link MediaRestController}'s generic upload endpoint.
 *
 * <p>Usage from a frontend: upload here first to get a permanent {@code fileId}, use that
 * as the {@code fileUrl}/{@code fileKey} value in {@code POST /api/v1/incidents/{id}/evidence}
 * or {@code POST /api/v1/disputes/{id}/evidences}, then resolve a short-lived, actually-loadable
 * URL on demand via {@code GET /{fileId}/signed-url} whenever the evidence needs to be displayed
 * (the {@code fileId} itself is not a browser-loadable URL).
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/media/evidence")
public class EvidenceUploadController {

    private final IKernelFileStoragePort kernelFileStoragePort;

    public EvidenceUploadController(IKernelFileStoragePort kernelFileStoragePort) {
        this.kernelFileStoragePort = kernelFileStoragePort;
    }

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EvidenceUploadResponse> upload(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "public", defaultValue = "false") boolean isPublic,
            @RequestPart("file") FilePart file) {
        return kernelFileStoragePort.uploadFile(file, documentType, isPublic, authorization)
                .map(EvidenceUploadController::toResponse);
    }

    @GetMapping("/{fileId}/signed-url")
    public Mono<SignedUrlResponse> signedUrl(
            @PathVariable UUID fileId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return kernelFileStoragePort.getSignedUrl(fileId, authorization).map(SignedUrlResponse::new);
    }

    @GetMapping("/{fileId}/metadata")
    public Mono<EvidenceUploadResponse> metadata(
            @PathVariable UUID fileId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return kernelFileStoragePort.getMetadata(fileId, authorization).map(EvidenceUploadController::toResponse);
    }

    private static EvidenceUploadResponse toResponse(KernelStoredFile stored) {
        return new EvidenceUploadResponse(
                stored.id(), stored.fileName(), stored.contentType(), stored.size(),
                stored.analysisStatus(), stored.visibility());
    }

    public record EvidenceUploadResponse(
            UUID fileId,
            String fileName,
            String contentType,
            long size,
            String analysisStatus,
            String visibility) {}

    public record SignedUrlResponse(String url) {}
}
