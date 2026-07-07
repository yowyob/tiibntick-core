package com.yowyob.tiibntick.core.resource.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(ResourceCoreProperties.class)
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.resource")
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.resource.adapter.out.persistence.repository")
public class ResourceCoreConfig {
}
