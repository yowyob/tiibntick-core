package com.yowyob.tiibntick.core.agency.sync.config;

import com.yowyob.tiibntick.core.sync.adapter.in.rest.SyncController;
import com.yowyob.tiibntick.core.sync.config.SyncCoreConfig;
import com.yowyob.tiibntick.core.sync.config.SyncKafkaConfig;
import com.yowyob.tiibntick.core.sync.config.SyncR2dbcConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@Import({SyncCoreConfig.class, SyncR2dbcConfig.class, SyncKafkaConfig.class})
@ComponentScan(
        basePackages = {
                "com.yowyob.tiibntick.core.agency.sync",
                "com.yowyob.tiibntick.core.sync.application.service",
                "com.yowyob.tiibntick.core.sync.adapter.out"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SyncController.class
        )
)
@EnableR2dbcRepositories(basePackages = {
        "com.yowyob.tiibntick.core.agency.sync.adapter.out.persistence",
        "com.yowyob.tiibntick.core.sync.adapter.out.persistence"
})
public class AgencySyncCoreAutoConfiguration {
}
