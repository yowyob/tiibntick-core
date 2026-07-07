package com.yowyob.tiibntick.core.billing.templates.port.inbound;

import com.yowyob.tiibntick.core.billing.templates.application.command.SaveCustomTemplateCommand;
import com.yowyob.tiibntick.core.billing.templates.domain.model.CustomPolicyTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Inbound port for saving and managing personal custom policy templates.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
public interface ISaveCustomTemplateUseCase {

    /**
     * Saves a customized template configuration for the actor to reuse later.
     *
     * @param command the save custom template command
     * @return Mono containing the persisted CustomPolicyTemplate
     */
    Mono<CustomPolicyTemplate> save(SaveCustomTemplateCommand command);

    /**
     * Lists all custom templates saved by a specific actor.
     *
     * @param ownerActorId the actor UUID
     * @return Flux of custom templates owned by this actor
     */
    Flux<CustomPolicyTemplate> listByOwner(String ownerActorId);

    /**
     * Deletes a custom template by ID. Only the owner can delete their template.
     *
     * @param customTemplateId the UUID of the custom template
     * @param ownerActorId     the requesting actor (must be the owner)
     * @return Mono&lt;Void&gt;
     */
    Mono<Void> delete(java.util.UUID customTemplateId, String ownerActorId);

    /**
     * Renames a custom template.
     *
     * @param customTemplateId the UUID of the custom template
     * @param ownerActorId     the requesting actor (must be the owner)
     * @param newName          the new display name
     * @return Mono containing the updated CustomPolicyTemplate
     */
    Mono<CustomPolicyTemplate> rename(java.util.UUID customTemplateId, String ownerActorId, String newName);
}
