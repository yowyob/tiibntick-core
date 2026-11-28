package com.yowyob.tiibntick.core.roles.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.adapter.out.permission.PermissionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Invalidates the local {@link PermissionCache} when a role or permission-assignment
 * change is published.
 *
 * <p>Listens to:
 * <ul>
 *   <li>{@code tnt.roles.permission-changed} — payload {@code {"tenantId": "<UUID>", "userId": "<UUID>"}}
 *       evicts that one entry; {@code {"tenantId": "<UUID>"}} (no userId) evicts the whole tenant;
 *       an empty/unparseable payload evicts everything (fail-safe).</li>
 * </ul>
 *
 * <p>Fixed 2026-07-23 (Audit n5 P-01) — {@code PermissionChangeEventPublisher} (same module)
 * now publishes here on every role assignment/revocation. The cache still self-expires via
 * {@code tnt.roles.permission-cache-ttl-seconds} as a fallback for any instance that misses
 * the broadcast.
 *
 * @author MANFOUO Braun
 */
@Component
public class PermissionCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheInvalidationListener.class);

    private final PermissionCache cache;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Decision D-168: the platform-admin cache is keyed by account, whatever the tenant of the event. */
    private com.yowyob.tiibntick.core.roles.application.service.PlatformAdminCache platformAdminCache;

    public PermissionCacheInvalidationListener(PermissionCache cache) {
        this.cache = cache;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPlatformAdminCache(com.yowyob.tiibntick.core.roles.application.service.PlatformAdminCache cache) {
        this.platformAdminCache = cache;
    }

    /** Decision D-176: confirmed agency/organisation roles, keyed by tenant and account. */
    private com.yowyob.tiibntick.core.roles.application.service.ScopedRoleConfirmationService scopedRoles;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setScopedRoleConfirmation(
            com.yowyob.tiibntick.core.roles.application.service.ScopedRoleConfirmationService scopedRoles) {
        this.scopedRoles = scopedRoles;
    }

    private void evictScopedRoles(UUID tenantId, UUID userId) {
        if (scopedRoles == null) return;
        if (tenantId != null && userId != null) scopedRoles.invalidate(tenantId, userId);
        else if (tenantId != null) scopedRoles.invalidateTenant(tenantId);
        else scopedRoles.invalidateAll();
    }

    private void evictPlatformAdmin(UUID userId) {
        if (platformAdminCache == null) return;
        if (userId != null) platformAdminCache.invalidate(userId); else platformAdminCache.invalidateAll();
    }

    @KafkaListener(
            topics = com.yowyob.tiibntick.common.kafka.TntTopics.ROLES_PERMISSION_CHANGED,
            // Every instance must see every invalidation (each holds its own in-memory cache): a group per
            // instance, starting at the end of the topic. A shared group would deliver a message to ONE instance.
            groupId = "#{'tnt-roles-cache-' + T(java.util.UUID).randomUUID()}",
            properties = "auto.offset.reset=latest",
            containerFactory = "rolesKafkaListenerContainerFactory")
    public void onPermissionChanged(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID tenantId = uuidOrNull(payload, "tenantId");
            UUID userId = uuidOrNull(payload, "userId");

            evictPlatformAdmin(userId);
            evictScopedRoles(tenantId, userId);
            if (tenantId != null && userId != null) {
                cache.invalidate(tenantId, userId);
                log.info("Invalidated permission cache for tenant={} user={}", tenantId, userId);
            } else if (tenantId != null) {
                cache.invalidateTenant(tenantId);
                log.info("Invalidated permission cache for tenant={}", tenantId);
            } else {
                cache.invalidateAll();
            }
        } catch (Exception e) {
            // Lot L7 check (spec §3.10): deliberately not rethrown. The effect of this listener is a local cache
            // eviction; for an unreadable record the safe effect is to evict everything, which is done here, so
            // nothing is lost by not dead-lettering it.
            log.warn("Failed to process tnt.roles.permission-changed event ({}); invalidating entire cache as a fail-safe.",
                    e.getMessage());
            evictPlatformAdmin(null);
            evictScopedRoles(null, null);
            cache.invalidateAll();
        }
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        try {
            return UUID.fromString(node.get(field).asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
