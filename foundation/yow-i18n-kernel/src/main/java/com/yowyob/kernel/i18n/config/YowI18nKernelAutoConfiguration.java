package com.yowyob.kernel.i18n.config;

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
@AutoConfiguration
@EnableConfigurationProperties(I18nKernelProperties.class)
public class YowI18nKernelAutoConfiguration {

    /**
     * Provides the JSON-backed locale message adapter.
     * Loads locale packs from /i18n/messages_{locale}.json on the classpath.
     */
    @Bean
    @ConditionalOnMissingBean
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
