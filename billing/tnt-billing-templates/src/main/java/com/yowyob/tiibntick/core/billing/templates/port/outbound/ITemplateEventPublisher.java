package com.yowyob.tiibntick.core.billing.templates.port.outbound;

import com.yowyob.tiibntick.core.billing.templates.domain.event.CustomTemplateSavedEvent;
import com.yowyob.tiibntick.core.billing.templates.domain.event.TemplateAppliedEvent;
import reactor.core.publisher.Mono;

/**
 * Outbound port for publishing domain events to the Kafka event bus.
 *
 * <p>Implementations are in the adapter layer (Kafka producer adapter).
 * The domain use cases depend only on this interface, keeping them
 * independent of any specific messaging technology.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface ITemplateEventPublisher {

    /**
     * Publishes a {@link TemplateAppliedEvent} to {@code tnt.billing.template.applied}.
     *
     * @param event the event to publish
     * @return Mono&lt;Void&gt; completing when the event is successfully sent
     */
    Mono<Void> publishTemplateApplied(TemplateAppliedEvent event);

    /**
     * Publishes a {@link CustomTemplateSavedEvent} to {@code tnt.billing.custom_template.saved}.
     *
     * @param event the event to publish
     * @return Mono&lt;Void&gt; completing when the event is successfully sent
     */
    Mono<Void> publishCustomTemplateSaved(CustomTemplateSavedEvent event);
}
