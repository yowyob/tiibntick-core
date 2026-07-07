package com.yowyob.tiibntick.core.notify.domain.model;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Aggregate representing a user's notification channel preferences.
 * Allows users to opt out of specific channels or limit notification types.
 *
 * @author MANFOUO Braun
 */
public class NotificationPreference {

    private final String userId;
    private final Set<NotificationChannel> activeChannels;
    private String preferredLanguage; // Locale tag, e.g., "fr_CM"
    private boolean notificationsEnabled;

    public NotificationPreference(String userId, Set<NotificationChannel> activeChannels,
            String preferredLanguage) {
        this.userId = userId;
        Set<NotificationChannel> ch = (activeChannels == null || activeChannels.isEmpty())
                ? EnumSet.allOf(NotificationChannel.class) : activeChannels;
        this.activeChannels = EnumSet.copyOf(ch);
        this.preferredLanguage = preferredLanguage;
        this.notificationsEnabled = true;
    }

    /**
     * Full reconstitution constructor.
     */
    public NotificationPreference(String userId,
            Set<NotificationChannel> activeChannels,
            String preferredLanguage,
            boolean notificationsEnabled) {
        this.userId = userId;
        Set<NotificationChannel> ch2 = (activeChannels == null || activeChannels.isEmpty())
                ? EnumSet.allOf(NotificationChannel.class) : activeChannels;
        this.activeChannels = EnumSet.copyOf(ch2);
        this.preferredLanguage = preferredLanguage;
        this.notificationsEnabled = notificationsEnabled;
    }

    public boolean acceptsChannel(NotificationChannel channel) {
        return notificationsEnabled && activeChannels.contains(channel);
    }

    public void disableChannel(NotificationChannel channel) {
        activeChannels.remove(channel);
    }

    public void enableChannel(NotificationChannel channel) {
        activeChannels.add(channel);
    }

    public String getUserId() {
        return userId;
    }

    public Set<NotificationChannel> getActiveChannels() {
        return Collections.unmodifiableSet(activeChannels);
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public boolean areNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setPreferredLanguage(String language) {
        this.preferredLanguage = language;
    }

    public void setNotificationsEnabled(boolean val) {
        this.notificationsEnabled = val;
    }
}
