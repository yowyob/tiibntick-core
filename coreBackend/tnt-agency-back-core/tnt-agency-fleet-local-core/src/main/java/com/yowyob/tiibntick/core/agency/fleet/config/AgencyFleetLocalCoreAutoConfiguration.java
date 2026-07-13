package com.yowyob.tiibntick.core.agency.fleet.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.agency.fleet")
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence")
public class AgencyFleetLocalCoreAutoConfiguration {
}
