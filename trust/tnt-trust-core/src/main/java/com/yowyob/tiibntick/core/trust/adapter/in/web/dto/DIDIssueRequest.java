package com.yowyob.tiibntick.core.trust.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Request DTO — Issue a DID for a deliverer actor.
 * Posted to {@code POST /tnt/trust/actors/did/issue}.
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DIDIssueRequest(
        String actorId,
        String tenantId,
        String publicKeyPem,
        String serviceEndpoint) {}
