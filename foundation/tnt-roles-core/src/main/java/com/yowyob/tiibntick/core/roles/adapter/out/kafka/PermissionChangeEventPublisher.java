package com.yowyob.tiibntick.core.roles.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.roles.application.port.out.IPermissionChangeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes {@link TntTopics#ROLES_PERMISSION_CHANGED} so every instance's local
 * {@code PermissionCache} can be invalidated as soon as a role assignment/revocation is
 * committed, instead of waiting out the cache TTL.
 *
 * <p>Fixed 2026-07-23 (Audit n°5 P-01) — this topic used to have no producer anywhere in the
 * repo, so RBAC permission changes were only ever picked up on TTL expiry
 * ({@code tnt.roles.permission-cache-ttl-seconds}). Sends directly via the shared
 * {@code tntKafkaTemplate} rather than the transactional outbox: a missed cache-invalidation
 * broadcast is not a durability incident — the TTL fallback documented on
 * {@code PermissionCacheInvalidationListener} already covers it — so this doesn't warrant the
 * same guarantees as a financial or business-state event.
 *
 * @author MANFOUO Braun
 */
@Component
public class PermissionChangeEventPublisher implements IPermissionChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(PermissionChangeEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PermissionChangeEventPublisher(@Qualifier("tntKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                                           ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** Evicts a single user's cached permissions in the given tenant. */
    @Override
    public void notifyChanged(UUID tenantId, UUID userId) {
        send(tenantId, tenantId.toString(), buildPayload(tenantId, userId));
    }

    /** Evicts every cached permission entry for the given tenant. */
    @Override
    public void notifyTenantChanged(UUID tenantId) {
        send(tenantId, tenantId.toString(), buildPayload(tenantId, null));
    }

    private Map<String, Object> buildPayload(UUID tenantId, UUID userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        if (userId != null) {
            payload.put("userId", userId.toString());
        }
        return payload;
    }

    private void send(UUID tenantId, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TntTopics.ROLES_PERMISSION_CHANGED, key, json);
        } catch (Exception e) {
            log.warn("Failed to publish {} for tenant={}: {}",
                    TntTopics.ROLES_PERMISSION_CHANGED, tenantId, e.getMessage());
        }
    }
}
