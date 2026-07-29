package com.yowyob.tiibntick.core.gofreelancer.adapter.out.storage;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

/**
 * Local filesystem implementation of {@link FileStoragePort}.
 *
 * <p>Files are stored under the {@code uploads/images/} directory at the
 * working directory root — the same path served by
 * {@code WebConfig#addResourceHandlers} under {@code /uploads/**}.
 *
 * <p>Naming convention: {@code <prefix>_<uuid>.<ext>}
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private static final String UPLOAD_DIR = "uploads/images";

    public LocalFileStorageAdapter() {
        // Ensure the upload directory exists at startup
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (Exception e) {
            log.warn("Could not pre-create upload directory '{}': {}", UPLOAD_DIR, e.getMessage());
        }
    }

    @Override
    public Mono<String> saveBase64Image(String base64Image, String prefix) {
        if (base64Image == null || base64Image.isBlank()) {
            return Mono.just("");
        }
        return Mono.fromCallable(() -> {
            // Strip optional data-URI header (data:image/png;base64,...)
            String data = base64Image.contains(",")
                    ? base64Image.substring(base64Image.indexOf(',') + 1)
                    : base64Image;

            byte[] bytes = Base64.getDecoder().decode(data);
            String filename = prefix + "_" + UUID.randomUUID() + ".png";
            Path target = Paths.get(UPLOAD_DIR, filename);
            Files.write(target, bytes);
            log.debug("Saved base64 image → {}", target);
            return UPLOAD_DIR + "/" + filename;
        }).onErrorResume(ex -> {
            log.error("Failed to save base64 image with prefix '{}': {}", prefix, ex.getMessage());
            return Mono.just("");
        });
    }

    @Override
    public Mono<String> saveFilePart(FilePart filePart, String prefix) {
        if (filePart == null) {
            return Mono.just("");
        }
        String originalName = filePart.filename();
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        String filename = prefix + "_" + UUID.randomUUID() + ext;
        Path target = Paths.get(UPLOAD_DIR, filename);

        return filePart.transferTo(target)
                .doOnSuccess(v -> log.debug("Saved file part → {}", target))
                .thenReturn(UPLOAD_DIR + "/" + filename)
                .onErrorResume(ex -> {
                    log.error("Failed to save file part '{}': {}", originalName, ex.getMessage());
                    return Mono.just("");
                });
    }

    @Override
    public Mono<Boolean> deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> {
            File file = new File(filePath);
            boolean deleted = file.delete();
            if (deleted) {
                log.debug("Deleted file: {}", filePath);
            } else {
                log.warn("Could not delete file (not found?): {}", filePath);
            }
            return deleted;
        }).onErrorReturn(false);
    }
}
