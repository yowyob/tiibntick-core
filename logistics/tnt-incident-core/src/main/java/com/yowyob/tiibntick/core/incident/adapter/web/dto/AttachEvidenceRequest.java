package com.yowyob.tiibntick.core.incident.adapter.web.dto;
import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import com.yowyob.tiibntick.core.incident.domain.enums.EvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;
/**
 * Request DTO for attaching evidence to an incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AttachEvidenceRequest {
    @NotNull EvidenceType evidenceType;
    @NotBlank String fileUrl;
    @NotBlank String mimeType;
    @NotNull UUID capturedByActorId;
    @NotNull ActorRole capturedByRole;
    String sha256Checksum;
    Double latitude;
    Double longitude;
}
