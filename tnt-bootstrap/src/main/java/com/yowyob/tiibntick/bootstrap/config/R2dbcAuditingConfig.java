package com.yowyob.tiibntick.bootstrap.config;

import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableR2dbcAuditing
public class R2dbcAuditingConfig {
    // “blank” is sufficient
}