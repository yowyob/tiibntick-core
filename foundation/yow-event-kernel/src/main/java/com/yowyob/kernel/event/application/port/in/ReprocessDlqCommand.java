package com.yowyob.kernel.event.application.port.in;

/**
 * Command record for reprocessing a Dead-Letter Queue entry.
 *
 * @param dlqEntryId the identifier of the DLQ entry to reprocess
 * @param tenantId   the owning tenant
 */
public record ReprocessDlqCommand(String dlqEntryId, String tenantId) {}
