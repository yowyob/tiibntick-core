package com.yowyob.tiibntick.core.agency.staff.adapter.out.clients;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.staff.application.port.out.AgencyEmployeeInvitePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Invites an employee through Core HRM ({@code /api/employees/invite} → Kernel).
 */
@Component
public class AgencyEmployeeInviteClient implements AgencyEmployeeInvitePort {

    private static final Logger log = LoggerFactory.getLogger(AgencyEmployeeInviteClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public AgencyEmployeeInviteClient(@Qualifier("agencyPlatformWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<InvitedEmployee> invite(InviteCommand command) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", command.firstName());
        body.put("lastName", command.lastName());
        body.put("email", command.email());
        body.put("password", command.password());
        if (command.kernelAgencyId() != null) {
            body.put("agencyId", command.kernelAgencyId());
        }

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/employees/invite")
                        .queryParam("organizationId", command.kernelOrganizationId())
                        .build())
                .header("X-Tenant-Id", command.tenantId().toString())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(AgencyEmployeeInviteClient::parseInvitedEmployee)
                .doOnSuccess(invited -> log.info(
                        "[AgencyInvite] Employee invited tenantId={} orgId={} userId={}",
                        command.tenantId(), command.kernelOrganizationId(), invited.userId()))
                .onErrorMap(WebClientResponseException.class, e -> {
                    log.warn("[AgencyInvite] Invite failed tenantId={} email={} status={}: {}",
                            command.tenantId(), command.email(), e.getStatusCode().value(), e.getResponseBodyAsString());
                    return new TntValidationException(
                            "STAFF_ACCOUNT_PROVISION_FAILED",
                            "Impossible de créer le compte d'accès pour " + command.email()
                                    + " (HTTP " + e.getStatusCode().value() + ").",
                            null);
                });
    }

    @SuppressWarnings("unchecked")
    private static InvitedEmployee parseInvitedEmployee(Map<String, Object> envelope) {
        if (envelope == null) {
            throw new TntValidationException("Empty employee invite response");
        }
        Object dataNode = envelope.get("data");
        Map<String, Object> data;
        if (dataNode instanceof Map<?, ?> map) {
            data = (Map<String, Object>) map;
        } else if (envelope.containsKey("userId") || envelope.containsKey("actorId")) {
            data = envelope;
        } else {
            throw new TntValidationException("Employee invite response missing data");
        }

        UUID userId = requireUuid(data, "userId");
        UUID actorId = optionalUuid(data, "actorId");
        if (actorId == null) {
            actorId = userId;
        }
        String email = data.get("email") != null ? data.get("email").toString() : null;
        return new InvitedEmployee(userId, actorId, email);
    }

    private static UUID requireUuid(Map<String, Object> data, String field) {
        UUID value = optionalUuid(data, field);
        if (value == null) {
            throw new TntValidationException("Employee invite response missing " + field);
        }
        return value;
    }

    private static UUID optionalUuid(Map<String, Object> data, String field) {
        Object raw = data.get(field);
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(raw.toString());
    }
}
