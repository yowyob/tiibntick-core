package com.yowyob.tiibntick.core.media.domain;

import java.util.UUID;

/**
 * Mirrors the Kernel's {@code StoredFileResponse} (file-controller, {@code POST /api/files}
 * and friends) — see {@code docs/kernel-api/schemas.md}.
 *
 * @author MANFOUO Braun
 */
public record KernelStoredFile(
        UUID id,
        UUID organizationId,
        UUID uploadedByUserId,
        String fileName,
        String contentType,
        long size,
        String documentType,
        String analysisStatus,
        String analysisReason,
        String visibility
) {
}
