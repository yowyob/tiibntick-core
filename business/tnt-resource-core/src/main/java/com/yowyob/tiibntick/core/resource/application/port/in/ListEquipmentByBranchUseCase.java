package com.yowyob.tiibntick.core.resource.application.port.in;

import com.yowyob.tiibntick.core.resource.domain.model.Equipment;
import com.yowyob.tiibntick.core.resource.domain.model.EquipmentStatus;
import reactor.core.publisher.Flux;
import java.util.UUID;

/**
 * Inbound port: list all equipment in a specific branch, optionally filtered by status.
 * @author MANFOUO Braun.
 */
public interface ListEquipmentByBranchUseCase {
    Flux<Equipment> listByBranch(UUID tenantId, UUID branchId, EquipmentStatus statusFilter);
}
