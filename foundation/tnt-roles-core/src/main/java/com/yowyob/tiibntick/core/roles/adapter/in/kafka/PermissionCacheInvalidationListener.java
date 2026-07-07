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
 * <p>No producer publishes to this topic yet anywhere in TiiBnTick Core — this listener is a
 * forward-compatible scaffold for when role/permission mutation flows (e.g. in
 * {@code tnt-administration-core}) start emitting change events. Until then it simply never
 * fires; the cache still self-expires via {@code tnt.roles.permission-cache-ttl-seconds}.
 *
 * @author MANFOUO Braun
 */
@Component
public class PermissionCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheInvalidationListener.class);

    private final PermissionCache cache;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PermissionCacheInvalidationListener(PermissionCache cache) {
        this.cache = cache;
    }

    @KafkaListener(
            topics = "tnt.roles.permission-changed",
            groupId = "${spring.kafka.consumer.group-id:tiibntick-core}",
            containerFactory = "rolesKafkaListenerContainerFactory")
    public void onPermissionChanged(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID tenantId = uuidOrNull(payload, "tenantId");
            UUID userId = uuidOrNull(payload, "userId");

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
            log.warn("Failed to process tnt.roles.permission-changed event ({}); invalidating entire cache as a fail-safe.",
                    e.getMessage());
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
