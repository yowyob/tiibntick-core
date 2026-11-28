package com.yowyob.kernel.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the event kernel.
 * Bound under the "yow.event" prefix in application.yaml.
 *
 * <p>Supported properties:
 * <pre>{@code
 * yow:
 *   event:
 *     outbox:
 *       batch-size: 50           # legacy OutboxPollerService (not wired)
 *       poll-interval-ms: 1000   # legacy OutboxPollerService (not wired)
 *       relay:                   # R2 — automatic re-publication of FAILED / stale PENDING envelopes
 *         enabled: true
 *         poll-interval-ms: 5000
 *         batch-size: 50
 *         max-attempts: 10       # total attempts, the initial direct publish included; then DEAD
 *         initial-delay: 10s     # backoff after the n-th failure: initial-delay * multiplier^(n-1)
 *         multiplier: 2.0
 *         max-delay: 10m
 *         pending-grace: 60s     # a PENDING envelope older than this is taken over
 *         claim-timeout: 2m      # a RETRYING claim older than this is taken over
 *     consumer-idempotency:      # duplicate filter applied to every @KafkaListener
 *       enabled: true
 *       retention: 14d
 *       purge-cron: "0 40 3 * * *"
 *       lookup-timeout: 5s
 *       excluded-topics: []
 * }</pre>
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "yow.event")
public class YowEventKernelProperties {

    private final Outbox outbox = new Outbox();
    private final ConsumerIdempotency consumerIdempotency = new ConsumerIdempotency();

    public Outbox getOutbox() { return outbox; }
    public ConsumerIdempotency getConsumerIdempotency() { return consumerIdempotency; }

    /**
     * Outbox poller tuning.
     */
    public static class Outbox {

        /** Maximum number of pending entries fetched per poll cycle. */
        private int batchSize = 50;

        /** Fixed delay (ms) between two consecutive outbox poll cycles. */
        private long pollIntervalMs = 1000;

        public int getBatchSize()              { return batchSize;       }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public long getPollIntervalMs()                  { return pollIntervalMs;       }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

        private final Relay relay = new Relay();

        public Relay getRelay() { return relay; }
    }

    /**
     * Automatic outbox relay (R2, decided 2026-11-26): re-publishes FAILED envelopes with an
     * exponential backoff, takes over PENDING envelopes left behind by a crash, and declares an
     * envelope DEAD (DLQ entry + ERROR log + metric) once {@link #maxAttempts} is reached.
     */
    public static class Relay {

        /** Master switch of the relay job. */
        private boolean enabled = true;

        /** Fixed delay between two relay cycles (ms) — also the delay before the first re-publication. */
        private long pollIntervalMs = 5_000;

        /** Maximum number of envelopes handled per cycle. */
        private int batchSize = 50;

        /** Total publish attempts (the initial direct publish included) before the envelope is DEAD. */
        private int maxAttempts = 10;

        /** Delay after the first failed attempt; then multiplied by {@link #multiplier} per failure. */
        private Duration initialDelay = Duration.ofSeconds(10);

        /** Exponential backoff multiplier (&gt;= 1). */
        private double multiplier = 2.0;

        /** Upper bound of the backoff delay. */
        private Duration maxDelay = Duration.ofMinutes(10);

        /** Minimum age of a PENDING envelope before the relay takes it over. */
        private Duration pendingGrace = Duration.ofSeconds(60);

        /** Lifetime of a RETRYING claim; an older claim belongs to a dead instance and is taken over. */
        private Duration claimTimeout = Duration.ofMinutes(2);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getInitialDelay() { return initialDelay; }
        public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
        public Duration getMaxDelay() { return maxDelay; }
        public void setMaxDelay(Duration maxDelay) { this.maxDelay = maxDelay; }
        public Duration getPendingGrace() { return pendingGrace; }
        public void setPendingGrace(Duration pendingGrace) { this.pendingGrace = pendingGrace; }
        public Duration getClaimTimeout() { return claimTimeout; }
        public void setClaimTimeout(Duration claimTimeout) { this.claimTimeout = claimTimeout; }
    }

    /**
     * Consumer-side duplicate filter (decided 2026-11-26): every {@code @KafkaListener} skips an
     * event its consumer group already processed successfully.
     */
    public static class ConsumerIdempotency {

        /** Master switch of the duplicate filter installed on every listener container factory. */
        private boolean enabled = true;

        /** How long a processed-event record is kept (duplicates older than this are not caught). */
        private Duration retention = Duration.ofDays(14);

        /** Cron of the purge of records older than {@link #retention}. */
        private String purgeCron = "0 40 3 * * *";

        /** Maximum wait for the duplicate lookup/marking; on timeout the record is processed (fail-open). */
        private Duration lookupTimeout = Duration.ofSeconds(5);

        /** Topics deliberately left unfiltered (none by default). */
        private List<String> excludedTopics = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getRetention() { return retention; }
        public void setRetention(Duration retention) { this.retention = retention; }
        public String getPurgeCron() { return purgeCron; }
        public void setPurgeCron(String purgeCron) { this.purgeCron = purgeCron; }
        public Duration getLookupTimeout() { return lookupTimeout; }
        public void setLookupTimeout(Duration lookupTimeout) { this.lookupTimeout = lookupTimeout; }
        public List<String> getExcludedTopics() { return excludedTopics; }
        public void setExcludedTopics(List<String> excludedTopics) { this.excludedTopics = excludedTopics; }
    }
}
