package com.yowyob.tiibntick.core.notify.domain.vo;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;

import java.util.Map;

/**
 * Value Object representing the i18n template reference before translation.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public record NotificationModel(
        String templateKey,
        String targetLanguage,
        Map<String, Object> parameters,
        NotificationPriority priority) {
    /**
     * Convenience factory without explicit priority (defaults to NORMAL).
     */
    public static NotificationModel of(String templateKey, String targetLanguage,
            Map<String, Object> parameters) {
        return new NotificationModel(templateKey, targetLanguage, parameters, NotificationPriority.NORMAL);
    }
}
