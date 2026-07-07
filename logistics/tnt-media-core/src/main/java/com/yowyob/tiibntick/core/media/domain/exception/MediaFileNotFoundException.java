package com.yowyob.tiibntick.core.media.domain.exception;

import com.yowyob.tiibntick.core.media.domain.MediaFileId;

/**
 * Thrown when a requested {@link com.yowyob.tiibntick.core.media.domain.MediaFile} is not found.
 *
 * @author MANFOUO Braun
 */
public class MediaFileNotFoundException extends RuntimeException {

    private final MediaFileId fileId;

    public MediaFileNotFoundException(MediaFileId fileId) {
        super("MediaFile not found: " + fileId);
        this.fileId = fileId;
    }

    public MediaFileId getFileId() {
        return fileId;
    }
}
