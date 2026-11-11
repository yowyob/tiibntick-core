package com.yowyob.tiibntick.core.actor.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** The Kernel's {@code StoredFileResponse} (file-controller): what it says about an uploaded file. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelStoredFile(
        UUID id,
        String fileName,
        String contentType,
        long size,
        String documentType,
        String analysisStatus,
        String analysisReason,
        String visibility) {
}
