package com.yowyob.kernel.i18n.config;

import com.yowyob.kernel.i18n.adapter.r2dbc.R2dbcMessageCatalogAdapter;
import com.yowyob.kernel.i18n.application.port.out.MessageCatalogPort;
import com.yowyob.kernel.i18n.application.service.MessageCatalog;
import com.yowyob.kernel.i18n.application.service.MessageCatalogAdminService;
import com.yowyob.kernel.i18n.application.service.MessageCatalogRefresher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.i18n.adapter.currency.JavaCurrencyFormatterAdapter;
import com.yowyob.kernel.i18n.adapter.json.JsonLocalePackAdapter;
import com.yowyob.kernel.i18n.application.port.in.PriceFormatterUseCase;
import com.yowyob.kernel.i18n.application.port.in.TranslateMessageUseCase;
import com.yowyob.kernel.i18n.application.port.out.PriceFormatterPort;
import com.yowyob.kernel.i18n.application.port.out.MessageTranslationPort;
import com.yowyob.kernel.i18n.application.service.PriceFormatterService;
import com.yowyob.kernel.i18n.application.service.TranslationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the yow-i18n-kernel module.
 * Provides translation and currency formatting beans to all consuming modules.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration",
        "org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration"})
@EnableConfigurationProperties(I18nKernelProperties.class)
public class YowI18nKernelAutoConfiguration {

    /**
     * Database-backed catalog, present when a reactive {@link DatabaseClient} exists (the whole TiiBnTick
     * application). Packaged JSON packs stay as the last-resort fallback, so a fresh or unreachable
     * database never produces a missing message.
     */
    @Bean
    @ConditionalOnBean(DatabaseClient.class)
    @ConditionalOnProperty(prefix = "yowyob.i18n", name = "catalog-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MessageCatalogPort.class)
    public MessageCatalogPort messageCatalogPort(DatabaseClient databaseClient) {
        return new R2dbcMessageCatalogAdapter(databaseClient);
    }

    @Bean
    @ConditionalOnBean(MessageCatalogPort.class)
    @ConditionalOnMissingBean(MessageCatalog.class)
    public MessageCatalog messageCatalog(MessageCatalogPort port,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper, I18nKernelProperties properties) {
        return new MessageCatalog(port, new JsonLocalePackAdapter(objectMapper,
                properties.getLocales().toArray(String[]::new)));
    }

    @Bean
    @ConditionalOnBean(MessageCatalog.class)
    public MessageCatalogRefresher messageCatalogRefresher(MessageCatalog catalog, I18nKernelProperties properties) {
        return new MessageCatalogRefresher(catalog, properties.getRefreshInterval());
    }

    @Bean
    @ConditionalOnBean(MessageCatalog.class)
    @ConditionalOnMissingBean(MessageCatalogAdminService.class)
    public MessageCatalogAdminService messageCatalogAdminService(MessageCatalogPort port, MessageCatalog catalog) {
        return new MessageCatalogAdminService(port, catalog);
    }

    /**
     * Provides the JSON-backed locale message adapter. Used on its own only when there is no database
     * (unit/slice contexts); otherwise {@link MessageCatalog} is the {@link MessageTranslationPort}.
     */
    @Bean
    @ConditionalOnMissingBean(MessageTranslationPort.class)
    public MessageTranslationPort messageTranslationPort(
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper,
            I18nKernelProperties properties) {
        String[] localesArray = properties.getLocales().toArray(String[]::new);
        return new JsonLocalePackAdapter(objectMapper, localesArray);
    }

    /**
     * Provides the translation use case implementation.
     */
    @Bean
    @ConditionalOnMissingBean
    public TranslateMessageUseCase translateMessageUseCase(MessageTranslationPort port) {
        return new TranslationService(port);
    }

    /**
     * Provides the Java-based currency formatter adapter.
     */
    @Bean
    @ConditionalOnMissingBean
    public PriceFormatterPort priceFormatterPort() {
        return new JavaCurrencyFormatterAdapter();
    }

    /**
     * Provides the price formatting use case implementation.
     */
    @Bean
    @ConditionalOnMissingBean
    public PriceFormatterUseCase priceFormatterUseCase(PriceFormatterPort priceFormatterPort,
                                                    I18nKernelProperties properties) {
        return new PriceFormatterService(priceFormatterPort, properties.getDefaultLanguage());
    }
}
