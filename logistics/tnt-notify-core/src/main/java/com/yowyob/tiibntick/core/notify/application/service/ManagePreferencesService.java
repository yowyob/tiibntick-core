package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationPreferencesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationPreferencePort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import reactor.core.publisher.Mono;

import java.util.EnumSet;

/**
 * Application service managing user notification preferences.
 *
 * @author MANFOUO Braun
 */
public class ManagePreferencesService implements IManageNotificationPreferencesUseCase {

    private final INotificationPreferencePort preferencePort;

    /**
     * Default locale tag applied to new preference records.
     */
    private static final String DEFAULT_LOCALE = "fr_CM";

    public ManagePreferencesService(INotificationPreferencePort preferencePort) {
        this.preferencePort = preferencePort;
    }

    @Override
    public Mono<NotificationPreference> getPreferences(String tenantId, String organizationId, String userId) {
        return preferencePort.findByUserId(tenantId, organizationId, userId)
                .switchIfEmpty(Mono.defer(() -> {
                    // Create default preferences (all channels active)
                    NotificationPreference defaut = new NotificationPreference(
                            userId,
                            tenantId,
                            organizationId,
                            EnumSet.allOf(NotificationChannel.class),
                            DEFAULT_LOCALE);
                    return preferencePort.save(defaut);
                }));
    }

    @Override
    public Mono<NotificationPreference> savePreferences(NotificationPreference preferences) {
        return preferencePort.save(preferences);
    }

    @Override
    public Mono<NotificationPreference> disableChannel(String tenantId, String organizationId, String userId,
            NotificationChannel channel) {
        return getPreferences(tenantId, organizationId, userId)
                .doOnNext(p -> p.disableChannel(channel))
                .flatMap(preferencePort::save);
    }

    @Override
    public Mono<NotificationPreference> enableChannel(String tenantId, String organizationId, String userId,
            NotificationChannel channel) {
        return getPreferences(tenantId, organizationId, userId)
                .doOnNext(p -> p.enableChannel(channel))
                .flatMap(preferencePort::save);
    }

    @Override
    public Mono<NotificationPreference> changeLanguage(String tenantId, String organizationId, String userId,
            String localeTag) {
        return getPreferences(tenantId, organizationId, userId)
                .doOnNext(p -> p.setPreferredLanguage(localeTag))
                .flatMap(preferencePort::save);
    }
}
