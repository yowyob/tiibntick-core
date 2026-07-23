package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO — Issue a DID for a deliverer actor.
 * Posted to {@code POST /tnt/trust/actors/did/issue}.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DIDIssueRequest(
        @NotBlank(message = "actorId is required") String actorId,
        String tenantId,
        @NotBlank(message = "publicKeyPem is required") String publicKeyPem,
        String serviceEndpoint) {}
