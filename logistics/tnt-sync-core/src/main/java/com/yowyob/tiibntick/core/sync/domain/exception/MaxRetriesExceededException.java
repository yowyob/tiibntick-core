package com.yowyob.tiibntick.core.sync.domain.exception;

public class MaxRetriesExceededException extends SyncException {
    public MaxRetriesExceededException(String operationId) {
        super("SYNC_MAX_RETRIES_EXCEEDED", "Max retries exceeded for operation: " + operationId);
    }
}
