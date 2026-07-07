package com.yowyob.tiibntick.core.incident.application.command;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.UUID;
/**
 * Command to report a driver voluntary or forced withdrawal.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class ReportDriverWithdrawalCommand {
    @NotNull UUID tenantId;
    @NotNull UUID agencyId;
    @NotNull UUID missionId;
    @NotNull UUID driverActorId;
    @NotNull ActorRole driverRole;
    @NotNull PlatformType platform;
    @NotNull IncidentType withdrawalType;
    String justification;
    List<UUID> affectedParcelIds;
    Double currentLat;
    Double currentLng;
}
