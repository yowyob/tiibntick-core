package com.yowyob.tiibntick.core.billing.templates.application.service;

import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateParameterValidationException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplateParameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application service responsible for validating custom parameter overrides
 * against a template's declared parameter constraints.
 *
 * <p>Also provides the utility to merge custom overrides with template defaults,
 * producing the effective parameter map used during DSL rule generation.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@Service
public class TemplateParameterValidationService {

    /**
     * Validates the custom parameter overrides against the template's constraints.
     * Returns the template if valid, or terminates the stream with an error.
     *
     * @param template         the template whose constraints define valid ranges
     * @param customParameters the custom overrides provided by the actor
     * @return Mono of the template (unchanged) if all parameters pass validation
     */
    public Mono<PolicyTemplate> validate(PolicyTemplate template, Map<String, String> customParameters) {
        if (customParameters == null || customParameters.isEmpty()) {
            return Mono.just(template);
        }

        List<String> errors = template.validateCustomValues(customParameters);

        if (!errors.isEmpty()) {
            log.warn("Template {} parameter validation failed: {}", template.getTemplateCode(), errors);
            return Mono.error(new TemplateParameterValidationException(template.getTemplateCode(), errors));
        }

        log.debug("Template {} parameters validated successfully. Custom overrides: {}",
                template.getTemplateCode(), customParameters.keySet());
        return Mono.just(template);
    }

    /**
     * Merges custom parameter overrides with the template's default values.
     *
     * <p>Custom values override defaults for the same key. Parameters not included
     * in {@code customParameters} retain their default values from the template.
     *
     * @param template         the template providing default values
     * @param customParameters custom overrides (may be empty)
     * @return merged effective parameter map (key → value string)
     */
    public Map<String, String> mergeWithDefaults(PolicyTemplate template, Map<String, String> customParameters) {
        Map<String, String> effective = new HashMap<>();

        // Start with all template defaults
        for (TemplateParameter param : template.getParameters()) {
            effective.put(param.getKey(), param.getDefaultValue());
        }

        // Apply custom overrides
        if (customParameters != null) {
            effective.putAll(customParameters);
        }

        log.debug("Merged parameters for template {}: {}", template.getTemplateCode(), effective);
        return effective;
    }
}
