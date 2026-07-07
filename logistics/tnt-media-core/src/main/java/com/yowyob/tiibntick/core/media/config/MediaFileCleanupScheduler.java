package com.yowyob.tiibntick.core.media.config;

import com.yowyob.tiibntick.core.media.port.inbound.IUploadMediaUseCase;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Scheduled task that purges expired {@link com.yowyob.tiibntick.core.media.domain.MediaFile}
 * records and their corresponding MinIO objects.
 * <p>
 * Runs daily at 02:00 AM (configurable via {@code tnt.media.cleanup-cron}).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaFileCleanupScheduler {

    private final IMediaRepository mediaRepository;
    private final IUploadMediaUseCase uploadMediaUseCase;

    @Scheduled(cron = "${tnt.media.cleanup-cron:0 0 2 * * *}")
    public void cleanupExpiredFiles() {
        log.info("Starting expired media files cleanup job");
        mediaRepository.findExpiredBefore(LocalDateTime.now())
                .flatMap(file -> {
                    log.debug("Purging expired file: {}", file.getId());
                    return uploadMediaUseCase.delete(file.getId(), file.getTenantId())
                            .onErrorResume(e -> {
                                log.warn("Failed to delete file {}: {}", file.getId(), e.getMessage());
                                return Mono.empty();
                            });
                })
                .count()
                .subscribe(
                        count -> log.info("Expired media cleanup done — purged {} files", count),
                        err -> log.error("Expired media cleanup failed", err));
    }
}
