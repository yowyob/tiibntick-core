package com.yowyob.tiibntick.core.resource.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)

/**
 * Thrown when a requested piece of equipment is not found.
 * @author MANFOUO Braun.
 */
public class EquipmentNotFoundException extends RuntimeException {

    private final UUID equipmentId;

    public EquipmentNotFoundException(UUID equipmentId) {
        super("Equipment not found: " + equipmentId);
        this.equipmentId = equipmentId;
    }

    public UUID getEquipmentId() { return equipmentId; }
}
