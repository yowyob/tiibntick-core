package com.yowyob.tiibntick.core.billing.templates.application.service;

import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateInactiveException;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotApplicableException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Domain service responsible for checking whether a given actor type is allowed
 * to use a specific template, and whether the template is currently active.
 *
 * <p>This service encapsulates applicability business rules that cut across multiple
 * use cases (ApplyTemplate, PreviewPrice, ListTemplates).
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
public class TemplateApplicabilityService {

    /**
     * Validates that the given template is active AND applicable to the given owner type.
     * Returns the template unchanged if checks pass, or terminates the stream with an error.
     *
     * @param template  the template to check
     * @param ownerType the actor type requesting access
     * @return Mono of the validated template, or error signal
     */
    public Mono<PolicyTemplate> validate(PolicyTemplate template, PolicyOwnerType ownerType) {
        // Check activation status first
        if (!template.isActive()) {
            log.warn("Template {} is inactive. Access attempted by ownerType={}", template.getTemplateCode(), ownerType);
            return Mono.error(new TemplateInactiveException(template.getTemplateCode()));
        }

        // Check applicability
        if (!template.isApplicableTo(ownerType)) {
            log.warn("Template {} is not applicable to ownerType={}. Applicable to: {}",
                    template.getTemplateCode(), ownerType, template.getApplicableTo());
            return Mono.error(new TemplateNotApplicableException(template.getTemplateCode(), ownerType));
        }

        log.debug("Template {} validated for ownerType={}", template.getTemplateCode(), ownerType);
        return Mono.just(template);
    }

    /**
     * Checks if a template is applicable to a given owner type without throwing an exception.
     * Used for filtering in list queries.
     *
     * @param template  the template to check
     * @param ownerType the actor type to check against
     * @return true if the template is applicable and active
     */
    public boolean isApplicableAndActive(PolicyTemplate template, PolicyOwnerType ownerType) {
        return template.isActive() && template.isApplicableTo(ownerType);
    }
}
