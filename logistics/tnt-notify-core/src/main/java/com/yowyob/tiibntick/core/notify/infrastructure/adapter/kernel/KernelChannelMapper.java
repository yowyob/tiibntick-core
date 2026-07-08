package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;

/**
 * Translates between TiiBnTick's {@link NotificationChannel} vocabulary and
 * the Kernel notification engine's channel enum
 * ({@code EMAIL, SMS, WHATSAPP, PUSH, WEBSOCKET}).
 *
 * @author MANFOUO Braun
 */
final class KernelChannelMapper {

    private KernelChannelMapper() {
    }

    static String toKernel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> "EMAIL";
            case SMS_LOCAL -> "SMS";
            case WHATSAPP -> "WHATSAPP";
            case PUSH_FCM -> "PUSH";
            case IN_APP_WEBSOCKET -> "WEBSOCKET";
        };
    }

    static NotificationChannel fromKernel(String kernelChannel) {
        return switch (kernelChannel) {
            case "EMAIL" -> NotificationChannel.EMAIL;
            case "SMS" -> NotificationChannel.SMS_LOCAL;
            case "WHATSAPP" -> NotificationChannel.WHATSAPP;
            case "PUSH" -> NotificationChannel.PUSH_FCM;
            case "WEBSOCKET" -> NotificationChannel.IN_APP_WEBSOCKET;
            default -> throw new IllegalArgumentException("Unknown Kernel notification channel: " + kernelChannel);
        };
    }
}
