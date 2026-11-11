package com.yowyob.kernel.i18n.config;

import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the i18n kernel.
 * Bound under the "yowyob.i18n" prefix in application.yaml.
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "yowyob.i18n")
public class I18nKernelProperties {

    /**
     * List of locale tags to load at startup (e.g., fr_CM, en_CM, pidgin_CM).
     * Defaults to the three Cameroonian locales.
     */
    private List<String> locales = List.of("fr_CM", "en_CM", "pidgin_CM");

    /**
     * Default language used when no locale is specified in a request.
     */
    private SupportedLanguage defaultLanguage = SupportedLanguage.FR_CM;

    /** How often each instance checks whether the catalog changed in the database (0 disables polling). */
    private java.time.Duration refreshInterval = java.time.Duration.ofSeconds(30);

    /** Set to false to ignore the database and serve only the packaged JSON packs. */
    private boolean catalogEnabled = true;

    public java.time.Duration getRefreshInterval()                         { return refreshInterval; }
    public void setRefreshInterval(java.time.Duration refreshInterval)     { this.refreshInterval = refreshInterval; }
    public boolean isCatalogEnabled()                                      { return catalogEnabled; }
    public void setCatalogEnabled(boolean catalogEnabled)                  { this.catalogEnabled = catalogEnabled; }

    public List<String> getLocales()               { return locales;          }
    public void setLocales(List<String> locales)   { this.locales = locales;  }

    public SupportedLanguage getDefaultLanguage()                         { return defaultLanguage;           }
    public void setDefaultLanguage(SupportedLanguage defaultLanguage)     { this.defaultLanguage = defaultLanguage; }
}
