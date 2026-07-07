package com.yowyob.tiibntick.core.sync.domain.exception;

public class SyncSessionNotFoundException extends SyncException {
    public SyncSessionNotFoundException(String sessionId) {
        super("SYNC_SESSION_NOT_FOUND", "Sync session not found: " + sessionId);
    }
}
