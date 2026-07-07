package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import reactor.core.publisher.Mono;

/**
 * Secondary port for dispatching messages to a specific delivery channel.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface IMessageProviderPort {

    /**
     * Returns true if this provider handles the given channel.
     */
    boolean supports(NotificationChannel channel);

    /**
     * Sends the message content to the physical destination.
     *
     * @param destination the target address (phone, FCM token, email)
     * @param content     the ready-to-send message string
     */
    Mono<Void> sendMessage(String destination, String content);
}
