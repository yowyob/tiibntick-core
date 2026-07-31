package com.yowyob.tiibntick.core.gofreelancer.adapter.out.chat;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.INegotiationChatPort;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * No-op stub for {@link INegotiationChatPort}.
 * Product BFFs may replace this bean with a real messaging adapter.
 *
 * <p>Registered as a fallback {@code @Bean} in {@code GoFreelancerPointCoreConfig}
 * via {@code @ConditionalOnMissingBean} — not as a {@code @Component}, which would
 * make the condition self-defeat during component scanning.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
public class NoOpNegotiationChatAdapter implements INegotiationChatPort {

    @Override
    public Mono<String> openNegotiationThread(UUID announcementId, UUID clientId, UUID freelancerId) {
        log.debug("[Chat] No-op openNegotiationThread announcement={} client={} freelancer={}",
                announcementId, clientId, freelancerId);
        return Mono.empty();
    }
}
