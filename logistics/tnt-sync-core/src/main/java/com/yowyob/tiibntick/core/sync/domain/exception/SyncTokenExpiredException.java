package com.yowyob.tiibntick.core.sync.domain.exception;

public class SyncTokenExpiredException extends SyncException {
    public SyncTokenExpiredException(String token) {
        super("SYNC_TOKEN_EXPIRED", "Sync token is expired or invalid: " + token);
    }
}
