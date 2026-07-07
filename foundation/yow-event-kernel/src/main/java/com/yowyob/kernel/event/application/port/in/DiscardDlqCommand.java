package com.yowyob.kernel.event.application.port.in;

/**
 * Command record for discarding a Dead-Letter Queue entry.
 *
 * <p>A discarded entry will no longer be eligible for reprocessing.
 * The {@code reason} field is mandatory to ensure that all discard
 * decisions are auditable.
 *
 * @param dlqEntryId the identifier of the DLQ entry to discard
 * @param tenantId   the owning tenant
 * @param reason     mandatory justification for the discard (stored in the DB)
 */
public record DiscardDlqCommand(String dlqEntryId, String tenantId, String reason) {

    public DiscardDlqCommand {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                "reason must not be blank when discarding a DLQ entry");
        }
    }
}
