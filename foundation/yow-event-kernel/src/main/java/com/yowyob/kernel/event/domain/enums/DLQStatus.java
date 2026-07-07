package com.yowyob.kernel.event.domain.enums;

/**
 * Status of an entry sitting in the Dead-Letter Queue.
 */
public enum DLQStatus {

    /** Waiting for manual or automated reprocessing. */
    WAITING,

    /** A reprocessing attempt is currently underway. */
    REPROCESSING,

    /** Successfully reprocessed and committed. */
    REPROCESSED,

    /** Explicitly discarded by an administrator; will not be retried. */
    DISCARDED
}
