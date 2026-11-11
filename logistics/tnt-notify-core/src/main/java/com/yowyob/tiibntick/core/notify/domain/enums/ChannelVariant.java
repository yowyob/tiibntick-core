package com.yowyob.tiibntick.core.notify.domain.enums;

/**
 * The flavour of a message each channel needs. The names are those of the catalog's variants
 * ({@code MessageVariant}); resolution falls back to the plain text of the same language when a flavour
 * has not been written, so a message works on every channel from day one.
 */
public final class ChannelVariant {

    private ChannelVariant() {
    }

    /** Variant of the main text (body) for a channel. */
    public static String body(NotificationChannel channel) {
        if (channel == null) {
            return "DEFAULT";
        }
        return switch (channel) {
            case PUSH_FCM -> "PUSH";
            case SMS_LOCAL -> "SMS";
            case EMAIL -> "EMAIL_BODY";
            case IN_APP_WEBSOCKET -> "IN_APP";
            case WHATSAPP -> "DEFAULT";
        };
    }

    /** Variant of the subject line, or {@code null} when the channel has no subject. */
    public static String subject(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL ? "EMAIL_SUBJECT" : null;
    }
}
