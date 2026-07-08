package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.tenant.CurrentTenantUseCase;
import com.yowyob.tiibntick.common.tenant.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Configuration
@Profile("test")
public class TestTenantConfig {

    @Bean
    public CurrentTenantUseCase currentTenantUseCase() {
        return () -> Mono.just(new TenantContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), // tenantId
            null, // organizationId
            null, // agencyId
            UUID.fromString("22222222-2222-2222-2222-222222222222"), // userId
            null  // actorId
        ));
    }
}