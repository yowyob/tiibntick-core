package com.yowyob.tiibntick.core.geo.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)

/**
 * Thrown when a requested geo entity (node, arc, hub, zone, POI) is not found.
 *
 * Author: MANFOUO Braun
 */
public class GeoNotFoundException extends RuntimeException {

    private final String entityType;
    private final String entityId;

    public GeoNotFoundException(String entityType, String entityId) {
        super(entityType + " not found: " + entityId);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public GeoNotFoundException(String entityType, String entityId, Throwable cause) {
        super(entityType + " not found: " + entityId, cause);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public String entityType() { return entityType; }
    public String entityId()   { return entityId; }
}
