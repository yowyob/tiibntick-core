package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.InAppMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Secondary port: the inbox store, i.e. the Kernel notification engine (channel IN_APP). */
public interface IInAppInboxPort {

    Mono<InAppMessage> send(UUID recipientUserId, String application, String title, String body,
                            java.util.Map<String, String> metadata);

    Flux<InAppMessage> mine(String application);

    Mono<InAppMessage> markRead(UUID messageId);
}
