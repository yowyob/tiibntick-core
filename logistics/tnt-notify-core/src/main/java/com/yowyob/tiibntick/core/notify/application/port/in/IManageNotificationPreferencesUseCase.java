package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import reactor.core.publisher.Mono;

/**
 * Primary port for managing user notification preferences.
 *
 * @author MANFOUO Braun
 */
public interface IManageNotificationPreferencesUseCase {

    /**
     * Returns current preferences for a user, creating defaults if absent.
     */
    Mono<NotificationPreference> getPreferences(String tenantId, String organizationId, String userId);

    /**
     * Saves or updates preferences for a user.
     */
    Mono<NotificationPreference> savePreferences(NotificationPreference preferences);

    /**
     * Disables a specific notification channel for a user.
     */
    Mono<NotificationPreference> disableChannel(String tenantId, String organizationId, String userId,
            NotificationChannel channel);

    /**
     * Enables a specific notification channel for a user.
     */
    Mono<NotificationPreference> enableChannel(String tenantId, String organizationId, String userId,
            NotificationChannel channel);

    /**
     * Changes the preferred locale for a user's notifications.
     */
    Mono<NotificationPreference> changeLanguage(String tenantId, String organizationId, String userId,
            String localeTag);
}
