package com.yowyob.tiibntick.core.i18n.config;

import com.yowyob.kernel.i18n.application.service.MessageCatalog;
import com.yowyob.kernel.i18n.application.service.MessageCatalogAdminService;
import com.yowyob.tiibntick.core.i18n.adapter.in.web.I18nAdminController;
import com.yowyob.tiibntick.core.i18n.adapter.in.web.I18nExceptionHandler;
import com.yowyob.tiibntick.core.i18n.adapter.in.web.I18nPublicController;
import com.yowyob.tiibntick.core.i18n.adapter.in.web.MyLanguageController;
import com.yowyob.tiibntick.core.i18n.application.port.in.ManageUserLanguageUseCase;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** REST endpoints of the i18n API; only in a reactive web application and once the services exist. */
@AutoConfiguration(after = TntI18nAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class TntI18nWebAutoConfiguration {

    @Bean
    @ConditionalOnBean(MessageCatalog.class)
    @ConditionalOnMissingBean
    I18nPublicController i18nPublicController(MessageCatalog catalog, TntI18nProperties properties) {
        return new I18nPublicController(catalog, properties);
    }

    @Bean
    @ConditionalOnBean(ManageUserLanguageUseCase.class)
    @ConditionalOnMissingBean
    MyLanguageController myLanguageController(ManageUserLanguageUseCase useCase) {
        return new MyLanguageController(useCase);
    }

    @Bean
    @ConditionalOnBean({MessageCatalogAdminService.class, MessageCatalog.class})
    @ConditionalOnMissingBean
    I18nAdminController i18nAdminController(MessageCatalogAdminService admin, MessageCatalog catalog) {
        return new I18nAdminController(admin, catalog);
    }

    @Bean
    @ConditionalOnMissingBean
    I18nExceptionHandler i18nExceptionHandler() {
        return new I18nExceptionHandler();
    }
}
