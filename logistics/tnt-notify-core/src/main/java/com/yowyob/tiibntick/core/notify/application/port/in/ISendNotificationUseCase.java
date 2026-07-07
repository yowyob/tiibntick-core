package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import reactor.core.publisher.Mono;

/**
 * Primary port for sending notifications across any supportsd channel.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface ISendNotificationUseCase {

    /**
     * Translates a message template and dispatches it to the recipient via the
     * specified channel.
     *
     * @param recipientId   the actor ID of the recipient
     * @param targetDestination the physical destination (phone number, FCM token,
     *                         email address)
     * @param model           the i18n message template with parameters
     * @param channel            the delivery channel
     * @return the persisted Notification with its final status
     */
    Mono<Notification> send(String recipientId,
            String targetDestination,
            NotificationModel model,
            NotificationChannel channel);
}
