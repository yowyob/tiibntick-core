package com.yowyob.tiibntick.core.i18n.config;

import com.yowyob.tiibntick.core.i18n.adapter.out.persistence.R2dbcUserLanguagePreferenceRepository;
import com.yowyob.tiibntick.core.i18n.application.port.out.UserLanguagePreferenceRepository;
import com.yowyob.tiibntick.core.i18n.application.service.UserLanguageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Wires the account-language service. Runs after the kernel's auto-configuration so
 * the message catalog already exists.
 */
@AutoConfiguration(
        afterName = {"com.yowyob.kernel.i18n.config.YowI18nKernelAutoConfiguration",
                "org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration",
                "org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration"})
@EnableConfigurationProperties(TntI18nProperties.class)
public class TntI18nAutoConfiguration {

    @Bean
    @ConditionalOnBean(DatabaseClient.class)
    @ConditionalOnMissingBean(UserLanguagePreferenceRepository.class)
    public UserLanguagePreferenceRepository userLanguagePreferenceRepository(DatabaseClient databaseClient) {
        return new R2dbcUserLanguagePreferenceRepository(databaseClient);
    }

    @Bean
    @ConditionalOnBean(UserLanguagePreferenceRepository.class)
    @ConditionalOnMissingBean(UserLanguageService.class)
    public UserLanguageService userLanguageService(UserLanguagePreferenceRepository repository,
                                                   TntI18nProperties properties) {
        return new UserLanguageService(repository, properties.getPreferenceCacheTtl());
    }
}
