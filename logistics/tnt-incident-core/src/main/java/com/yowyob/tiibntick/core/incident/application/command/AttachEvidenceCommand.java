package com.yowyob.tiibntick.core.incident.application.command;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.EvidenceType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.UUID;
/**
 * Command to attach a digital evidence file to an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class AttachEvidenceCommand {
    @NotNull UUID incidentId;
    @NotNull EvidenceType evidenceType;
    @NotNull String fileUrl;
    @NotNull String mimeType;
    @NotNull UUID capturedByActorId;
    @NotNull ActorRole capturedByRole;
    String sha256Checksum;
    Double latitude;
    Double longitude;
}
