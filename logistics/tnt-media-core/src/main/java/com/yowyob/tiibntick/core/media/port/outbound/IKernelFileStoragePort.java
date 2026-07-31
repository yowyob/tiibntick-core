package com.yowyob.tiibntick.core.media.port.outbound;

import com.yowyob.tiibntick.core.media.domain.KernelStoredFile;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — the Kernel's {@code file-controller} (full file lifecycle: upload,
 * content analysis, signed-url, metadata), consumed for incident/dispute evidence uploads
 * so evidence is stored/scanned by the Kernel rather than TiiBnTick's own MinIO bucket.
 *
 * <p>Deliberately separate from {@link IObjectStorageClient} (MinIO, bucket/objectKey-shaped,
 * used by the rest of {@code tnt-media-core}'s generic upload flow) — the Kernel's file model
 * is {@code fileId}-centric with no bucket concept, so forcing it into that interface would
 * distort both. Implemented by {@code KernelFileStorageAdapter}.
 *
 * @author MANFOUO Braun
 */
public interface IKernelFileStoragePort {

    /**
     * Uploads a file to the Kernel ({@code POST /api/files}).
     *
     * @param file             the multipart file part received from the caller
     * @param documentType     free-form document-type hint (e.g. {@code "INCIDENT_EVIDENCE"},
     *                         {@code "DISPUTE_EVIDENCE"})
     * @param isPublic         whether the file should be publicly readable without a signed URL
     * @param bearerAuthorization forwarded {@code Authorization} header, or null
     * @return the stored file's Kernel-side metadata (carries the {@code fileId} to persist)
     */
    Mono<KernelStoredFile> uploadFile(FilePart file, String documentType, boolean isPublic, String bearerAuthorization);

    /**
     * Requests a temporary signed URL for a private file ({@code GET /api/files/{fileId}/signed-url}).
     */
    Mono<String> getSignedUrl(UUID fileId, String bearerAuthorization);

    /**
     * Fetches a stored file's metadata ({@code GET /api/files/{fileId}/metadata}).
     */
    Mono<KernelStoredFile> getMetadata(UUID fileId, String bearerAuthorization);
}
