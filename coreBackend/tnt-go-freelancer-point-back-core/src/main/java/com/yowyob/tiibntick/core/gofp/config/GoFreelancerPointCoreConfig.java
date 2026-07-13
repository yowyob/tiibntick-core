package com.yowyob.tiibntick.core.gofp.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Configuration Spring du module tnt-go-freelancer-point-back-core.
 *
 * Importée par TntCoreConfig dans tnt-bootstrap.
 * Active le scan des composants et les repositories R2DBC du module.
 */
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.gofp")
@EnableR2dbcRepositories(
    basePackages = "com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository"
)
public class GoFreelancerPointCoreConfig {
    // Pas de beans supplémentaires nécessaires :
    // les adapters, services et contrôleurs sont auto-détectés via @ComponentScan.
    // Les beans Kafka et WebClient sont fournis par tnt-bootstrap (TntCoreConfig / TntKafkaConfig).
}
