package com.yowyob.tiibntick.core.agency.org.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for {@code tnt-agency-org-core} — agency registry in schema {@code agency_org}.
 */
@Configuration
@EnableScheduling
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.agency.org")
@EnableR2dbcRepositories(basePackages = {
        "com.yowyob.tiibntick.core.agency.org.adapter.out.persistence",
        "com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence"
})
public class AgencyOrgCoreAutoConfiguration {
}
