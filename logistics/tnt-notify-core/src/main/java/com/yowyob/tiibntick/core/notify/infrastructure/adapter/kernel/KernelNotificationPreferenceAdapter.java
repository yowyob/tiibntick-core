package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationPreferencePort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationPreferenceDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SavePreferenceRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges {@link INotificationPreferencePort} to the Kernel notification
 * engine's per-(user, channel) preference rows
 * ({@code /api/notifications/preferences}).
 *
 * <p>TiiBnTick models a user's preferences as a single aggregate (one
 * {@link NotificationPreference} per user, holding a set of active
 * channels + one locale). The Kernel models one row per channel. This
 * adapter reconciles the two shapes: {@link #findByUserId} merges the
 * Kernel's per-channel rows into one aggregate, and {@link #save} fans a
 * save of the aggregate out into one upsert per {@link NotificationChannel}.
 *
 * <p>Active by default; set {@code tnt.notify.kernel.enabled=false} to fall
 * back to local R2DBC persistence instead (see
 * {@link com.yowyob.tiibntick.core.notify.config.NotifyCoreAutoConfiguration}).
 *
 * @author MANFOUO Braun
 */
public class KernelNotificationPreferenceAdapter implements INotificationPreferencePort {

    private static final Logger log = LoggerFactory.getLogger(KernelNotificationPreferenceAdapter.class);

    private final KernelNotificationClient client;

    public KernelNotificationPreferenceAdapter(KernelNotificationClient client) {
        this.client = client;
    }

    @Override
    public Mono<NotificationPreference> findByUserId(String tenantId, String organizationId, String userId) {
        return client.listPreferences(tenantId, organizationId, userId)
                .collectList()
                .flatMap(rows -> rows.isEmpty()
                        ? Mono.empty()
                        : Mono.just(toDomain(userId, tenantId, organizationId, rows)));
    }

    @Override
    public Mono<NotificationPreference> save(NotificationPreference preferences) {
        UUID userId = UUID.fromString(preferences.getUserId());
        Set<NotificationChannel> active = preferences.getActiveChannels();
        String tenantId = preferences.getTenantId();
        String organizationId = preferences.getOrganizationId();

        return Flux.fromArray(NotificationChannel.values())
                .flatMap(channel -> client.savePreference(tenantId, organizationId, new SavePreferenceRequestDto(
                        userId,
                        KernelChannelMapper.toKernel(channel),
                        preferences.areNotificationsEnabled() && active.contains(channel),
                        preferences.getPreferredLanguage())))
                .doOnComplete(() -> log.debug("Saved Kernel notification preferences for user {}", userId))
                .then(findByUserId(tenantId, organizationId, preferences.getUserId()))
                .switchIfEmpty(Mono.just(preferences));
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private NotificationPreference toDomain(String userId, String tenantId, String organizationId,
            java.util.List<NotificationPreferenceDto> rows) {
        Set<NotificationChannel> activeChannels = EnumSet.noneOf(NotificationChannel.class);
        String preferredLanguage = null;
        boolean anyEnabled = false;

        for (NotificationPreferenceDto row : rows) {
            NotificationChannel channel = KernelChannelMapper.fromKernel(row.channel());
            if (row.enabled()) {
                activeChannels.add(channel);
                anyEnabled = true;
            }
            if (row.locale() != null) {
                preferredLanguage = row.locale();
            }
        }

        return new NotificationPreference(userId, tenantId, organizationId, activeChannels, preferredLanguage,
                anyEnabled);
    }
}
