package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * Outbound port for file storage operations.
 * Decouples the application layer from the filesystem/cloud storage.
 */
public interface FileStoragePort {

    /**
     * Saves a base64-encoded image and returns its path.
     *
     * @param base64Image the base64 encoded image
     * @param prefix      filename prefix
     * @return the relative path of the saved file
     */
    Mono<String> saveBase64Image(String base64Image, String prefix);

    /**
     * Saves a multipart file and returns its path.
     *
     * @param filePart the multipart file
     * @param prefix   filename prefix
     * @return the relative path of the saved file
     */
    Mono<String> saveFilePart(FilePart filePart, String prefix);

    /**
     * Deletes a file by path.
     *
     * @param filePath the path of the file to delete
     * @return true if deleted, false otherwise
     */
    Mono<Boolean> deleteFile(String filePath);

    /**
     * Lot C-54 — saves already validated bytes under {@code prefix_<uuid>.<extension>} and returns
     * the relative path, or an empty string when the write fails (like the other methods).
     */
    default Mono<String> saveBytes(byte[] bytes, String prefix, String extension) {
        return Mono.error(new UnsupportedOperationException("saveBytes not supported by " + getClass()));
    }
}
