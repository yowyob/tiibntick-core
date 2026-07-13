package com.yowyob.tiibntick.core.agency.assignment.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.agency.assignment")
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence")
public class AgencyAssignmentCoreAutoConfiguration {
}
