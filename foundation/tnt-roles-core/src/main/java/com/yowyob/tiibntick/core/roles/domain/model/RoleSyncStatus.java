package com.yowyob.tiibntick.core.roles.domain.model;

/**
 * Lifecycle status of a {@link RoleSyncOutboxEntry}.
 *
 * <p>State machine (see {@link RoleSyncOutboxEntry} for the transition methods):
 * <pre>
 *   PENDING ──(picked up by poller)──&gt; PROCESSING ──(Kernel call succeeds)──&gt; PROVISIONED
 *                                          │
 *                                          ├──(Kernel call fails, retries left)──&gt; RETRYING ──(picked up again)──&gt; PROCESSING
 *                                          │
 *                                          └──(Kernel call fails, retries exhausted)──&gt; DEAD
 * </pre>
 * {@code FAILED} is a terminal-per-attempt marker reserved for a single failed attempt that
 * is about to be retried in the same transition (see {@link RoleSyncOutboxEntry#asRetrying});
 * it never appears as a row's persisted status on its own — a row is either actively
 * {@code RETRYING} (with a scheduled {@code next_attempt_at}) or has moved to the terminal
 * {@code DEAD} status once retries are exhausted.
 *
 * @author MANFOUO Braun
 */
public enum RoleSyncStatus {
    PENDING,
    PROCESSING,
    PROVISIONED,
    FAILED,
    RETRYING,
    DEAD
}
