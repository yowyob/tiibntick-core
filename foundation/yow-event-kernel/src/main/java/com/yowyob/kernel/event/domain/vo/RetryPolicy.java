package com.yowyob.kernel.event.domain.vo;

import java.time.Duration;

/**
 * Immutable retry configuration used by {@link yowyob.kernel.event.domain.model.OutboxEntry}
 * and {@link yowyob.kernel.event.domain.model.DeadLetterEntry}.
 *
 * <p>Implements an exponential back-off strategy with a configurable multiplier
 * and maximum delay cap. The delay for attempt {@code n} (0-based) is:
 * <pre>
 *   delay(n) = min(initialDelayMs * multiplier^n, maxDelayMs)
 * </pre>
 *
 * <p>Instances should be created via the provided factory methods rather than
 * the canonical constructor to ensure invariants are enforced.
 *
 * @param maxAttempts   maximum number of delivery attempts before the entry is
 *                      moved to the DLQ
 * @param initialDelayMs delay in milliseconds before the first retry
 * @param multiplier    exponential back-off multiplier (must be &gt;= 1.0)
 * @param maxDelayMs    upper bound on the computed delay in milliseconds
 */
public record RetryPolicy(int maxAttempts, long initialDelayMs, double multiplier, long maxDelayMs) {

    // ── Validation ──────────────────────────────────────────────────────────

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException("initialDelayMs must be >= 0");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0, got: " + multiplier);
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("maxDelayMs must be >= initialDelayMs");
        }
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /**
     * Creates the default retry policy used for transactional outbox entries.
     * <ul>
     *   <li>Max attempts: 5</li>
     *   <li>Initial delay: 1 second</li>
     *   <li>Multiplier: 2.0 (doubles each attempt)</li>
     *   <li>Max delay: 60 seconds</li>
     * </ul>
     *
     * @return the default outbox retry policy
     */
    public static RetryPolicy defaultOutboxPolicy() {
        return new RetryPolicy(5, 1_000L, 2.0, 60_000L);
    }

    /**
     * Creates a lenient retry policy suitable for DLQ reprocessing, where a
     * human operator may need time to fix the underlying issue.
     *
     * @return the DLQ retry policy (3 attempts, 5-minute initial delay)
     */
    public static RetryPolicy defaultDlqPolicy() {
        return new RetryPolicy(3, 300_000L, 2.0, 3_600_000L);
    }

    /**
     * Creates a fast retry policy for low-latency event consumers.
     *
     * @return the fast retry policy (3 attempts, 200ms initial delay)
     */
    public static RetryPolicy fastPolicy() {
        return new RetryPolicy(3, 200L, 1.5, 5_000L);
    }

    // ── Behaviour ────────────────────────────────────────────────────────────

    /**
     * Computes the back-off delay before attempt {@code attemptNumber} (1-based).
     *
     * @param attemptNumber the ordinal of the next attempt, starting at 1
     * @return the computed {@link Duration} to wait before that attempt
     */
    public Duration nextDelay(final int attemptNumber) {
        if (attemptNumber <= 1) {
            return Duration.ofMillis(initialDelayMs);
        }
        // Exponent is (attemptNumber - 1) so that the first retry uses initialDelayMs
        double computed = initialDelayMs * Math.pow(multiplier, attemptNumber - 1);
        long capped = Math.min((long) computed, maxDelayMs);
        return Duration.ofMillis(capped);
    }

    /**
     * Returns {@code true} when the given attempt count has reached or exceeded
     * the maximum configured attempts, meaning no further retries should occur.
     *
     * @param currentAttemptCount the total number of attempts already made
     * @return {@code true} if the maximum has been exceeded
     */
    public boolean hasExceededMaxAttempts(final int currentAttemptCount) {
        return currentAttemptCount >= maxAttempts;
    }
}
