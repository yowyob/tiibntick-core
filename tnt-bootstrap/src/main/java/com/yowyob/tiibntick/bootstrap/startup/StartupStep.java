package com.yowyob.tiibntick.bootstrap.startup;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Represents a single ordered step in the {@link TntStartupSequence}.
 * Each step is either mandatory (failure stops the boot) or optional (failure degrades).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Getter
public class StartupStep {

    private final int order;
    private final String name;
    private final String description;
    private final boolean mandatory;

    private StepStatus status = StepStatus.PENDING;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    public StartupStep(int order, String name, String description, boolean mandatory) {
        this.order = order;
        this.name = name;
        this.description = description;
        this.mandatory = mandatory;
    }

    public void markInProgress() {
        this.status = StepStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
        log.info("[{}/9] Starting: {} — {}", order, name, description);
    }

    public void markSuccess() {
        this.status = StepStatus.SUCCESS;
        this.completedAt = LocalDateTime.now();
        long ms = startedAt != null
                ? Duration.between(startedAt, completedAt).toMillis()
                : 0;
        log.info("[{}/9] ✅ Completed: {} ({}ms)", order, name, ms);
    }

    public void markFailed(String reason) {
        this.status = StepStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = reason;
        if (mandatory) {
            log.error("[{}/9] ❌ CRITICAL FAILURE: {} — {}", order, name, reason);
        } else {
            log.warn("[{}/9] ⚠️ Optional step failed: {} — {}", order, name, reason);
        }
    }

    public void markSkipped(String reason) {
        this.status = StepStatus.SKIPPED;
        this.completedAt = LocalDateTime.now();
        log.info("[{}/9] ⏭️ Skipped: {} — {}", order, name, reason);
    }

    public boolean isCriticalFailure() {
        return mandatory && status == StepStatus.FAILED;
    }
}
