package com.yowyob.tiibntick.core.agency.workforce.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.agency.workforce")
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence")
public class AgencyWorkforceCoreAutoConfiguration {
}
