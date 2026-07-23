package com.yowyob.tiibntick.core.organization.application.port.out;

import com.yowyob.tiibntick.core.organization.domain.event.HubRelaisUpdatedEvent;
import reactor.core.publisher.Mono;

/**
 * Outbound event publishing port for {@code HubRelais} domain events.
 *
 * <p>This is a secondary port (driven port) in the hexagonal architecture.
 *
 * @author MANFOUO Braun
 */
public interface HubEventPublisherPort {

    /**
     * Publishes a {@link HubRelaisUpdatedEvent} to the event bus.
     *
     * @param event the event to publish
     * @return a {@link Mono} completing when the event is published
     */
    Mono<Void> publishHubUpdated(HubRelaisUpdatedEvent event);
}
