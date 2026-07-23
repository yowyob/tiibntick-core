package com.yowyob.tiibntick.core.media.config;

import com.yowyob.tiibntick.core.media.port.inbound.IUploadMediaUseCase;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

    /**
     * Chantier D · Audit n°6 · S2 — distributed lock so only one instance runs this per tick.
     * {@code lockAtLeastFor} matters here specifically because the method fires the actual
     * work off via {@code .subscribe()} and returns immediately — without a minimum hold
     * time, ShedLock would release the lock right after this fast return, before the
     * subscribed cleanup actually finishes, letting another instance pick it up again.
     */
    @Scheduled(cron = "${tnt.media.cleanup-cron:0 0 2 * * *}")
    @SchedulerLock(name = "media-cleanup-expired-files", lockAtMostFor = "PT30M", lockAtLeastFor = "PT2M")
    public void cleanupExpiredFiles() {
        LockAssert.assertLocked();
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
