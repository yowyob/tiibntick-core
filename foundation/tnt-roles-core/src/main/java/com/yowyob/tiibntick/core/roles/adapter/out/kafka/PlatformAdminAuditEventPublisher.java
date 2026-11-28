package com.yowyob.tiibntick.core.roles.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.roles.application.port.out.IPlatformAdminAlertPort;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit event of decision D-172 on {@link TntTopics#ROLES_PLATFORM_ADMIN_CHANGED}: {@code GRANTED} or
 * {@code REVOKED}, with the granter or revoker and the source tenant. Sent directly, like the permission-change
 * broadcast: the registry row is the durable record, the event is the feed of the administration platform.
 *
 * @author MANFOUO Braun
 */
@Component
public class PlatformAdminAuditEventPublisher implements IPlatformAdminAlertPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PlatformAdminAuditEventPublisher(@Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> granted(PlatformRoleGrant grant, UUID systemOwnerUserId) {
        return send("GRANTED", grant);
    }

    @Override
    public Mono<Void> revoked(PlatformRoleGrant grant, UUID systemOwnerUserId) {
        return send("REVOKED", grant);
    }

    private Mono<Void> send(String type, PlatformRoleGrant g) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload(type, g)))
                .flatMap(json -> Mono.fromFuture(kafkaTemplate.send(TntTopics.ROLES_PLATFORM_ADMIN_CHANGED,
                        g.userId().toString(), json)))
                .then();
    }

    static Map<String, Object> payload(String type, PlatformRoleGrant g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("grantId", g.id().toString());
        m.put("roleCode", g.roleCode());
        m.put("userId", g.userId().toString());
        m.put("grantKind", g.kind().name());
        m.put("grantedBy", g.grantedBy().toString());
        m.put("sourceTenantId", g.sourceTenantId().toString());
        m.put("systemTenantAtGrant", g.systemTenantAtGrant().toString());
        m.put("grantedAt", String.valueOf(g.grantedAt()));
        if (g.revokedAt() != null) {
            m.put("revokedAt", g.revokedAt().toString());
            m.put("revokedBy", String.valueOf(g.revokedBy()));
            if (g.revokeReason() != null) m.put("revokeReason", g.revokeReason());
            if (g.revokeBatchId() != null) m.put("revokeBatchId", g.revokeBatchId().toString());
        }
        return m;
    }
}
