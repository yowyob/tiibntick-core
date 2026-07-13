package com.yowyob.tiibntick.core.agency.onboarding.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.agency.onboarding")
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.agency.onboarding.adapter.out.persistence")
@EnableConfigurationProperties(AgencyOnboardingProperties.class)
public class AgencyOnboardingCoreAutoConfiguration {
}
