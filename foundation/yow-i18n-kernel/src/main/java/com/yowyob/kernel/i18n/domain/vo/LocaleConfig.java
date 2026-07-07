package com.yowyob.kernel.i18n.domain.vo;

import com.yowyob.kernel.i18n.domain.enums.SupportedCurrency;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;

import java.util.Objects;

/**
 * Value Object encapsulating a locale configuration: language + currency pair.
 * Used across all TiiBnTick sub-platforms for consistent locale resolution.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public record LocaleConfig(SupportedLanguage language, SupportedCurrency currency) {

    public LocaleConfig {
        Objects.requireNonNull(language, "Language must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
    }

    /**
     * Default locale for Cameroon: French + XAF.
     */
    public static LocaleConfig cameroonFrancophone() {
        return new LocaleConfig(SupportedLanguage.FR_CM, SupportedCurrency.XAF);
    }

    /**
     * Default locale for Anglophone Cameroon: English + XAF.
     */
    public static LocaleConfig cameroonAnglophone() {
        return new LocaleConfig(SupportedLanguage.EN_CM, SupportedCurrency.XAF);
    }

    /**
     * Default locale for Nigeria: English + NGN.
     */
    public static LocaleConfig nigeria() {
        return new LocaleConfig(SupportedLanguage.EN_NG, SupportedCurrency.NGN);
    }

    /**
     * Returns the locale tag used to load JSON message files (e.g., "fr_CM").
     */
    public String toLocaleTag() {
        return language.name().toLowerCase().replace("_", "_");
    }
}
