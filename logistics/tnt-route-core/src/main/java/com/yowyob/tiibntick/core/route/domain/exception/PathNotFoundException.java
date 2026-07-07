package com.yowyob.tiibntick.core.route.domain.exception;

public class PathNotFoundException extends RuntimeException {
    private final String originId;
    private final String destinationId;
    public PathNotFoundException(String originId, String destinationId) {
        super("No path found from " + originId + " to " + destinationId);
        this.originId = originId;
        this.destinationId = destinationId;
    }
    public String originId() { return originId; }
    public String destinationId() { return destinationId; }
}
