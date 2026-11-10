package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Keeps the authenticated-caller types injected by {@code @CurrentUser} out of the generated
 * OpenAPI contract. Without this, controllers that forget {@code @Parameter(hidden = true)}
 * (e.g. the Go/Freelancer ones taking a {@link TntSecurityContext}) leak bogus {@code ctx} /
 * {@code securityContext} query parameters, and generated clients wrongly treat them as inputs.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiIgnoredParametersConfig {

    @PostConstruct
    void ignoreCallerTypes() {
        SpringDocUtils.getConfig()
                .addRequestWrapperToIgnore(TntSecurityContext.class, TntUserIdentity.class);
    }
}
