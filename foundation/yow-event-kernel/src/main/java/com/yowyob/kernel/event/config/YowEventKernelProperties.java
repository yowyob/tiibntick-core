package com.yowyob.kernel.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the event kernel.
 * Bound under the "yow.event" prefix in application.yaml.
 *
 * <p>Supported properties:
 * <pre>{@code
 * yow:
 *   event:
 *     outbox:
 *       batch-size: 50           # max entries fetched per poll cycle
 *       poll-interval-ms: 1000   # delay between two poll cycles (ms)
 * }</pre>
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "yow.event")
public class YowEventKernelProperties {

    private final Outbox outbox = new Outbox();

    public Outbox getOutbox() { return outbox; }

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
    }
}
