package com.yowyob.kernel.i18n.adapter.currency;

import com.yowyob.kernel.i18n.application.port.out.PriceFormatterPort;
import com.yowyob.kernel.i18n.domain.enums.SupportedCurrency;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter that formats monetary amounts using Java's Locale system.
 * Handles African locale quirks: XAF has no decimal places, groups with spaces in French.
 *
 * @author MANFOUO Braun
 */
public class JavaCurrencyFormatterAdapter implements PriceFormatterPort {

    /**
     * Mapping from TiiBnTick language tags to Java Locale instances.
     * Using explicit mapping rather than Locale.forLanguageTag() for precise control.
     */
    private static final Map<SupportedLanguage, Locale> LOCALE_MAP = Map.of(
            SupportedLanguage.FR_CM,      Locale.FRANCE,
            SupportedLanguage.EN_CM,      Locale.UK,
            SupportedLanguage.PIDGIN_CM,  Locale.UK,
            SupportedLanguage.EN_NG,      Locale.UK,
            SupportedLanguage.FR_FR,      Locale.FRANCE,
            SupportedLanguage.EN_US,      Locale.US
    );

    @Override
    public String formatAmount(BigDecimal amount, SupportedCurrency currency, SupportedLanguage language) {
        Locale javaLocale = LOCALE_MAP.getOrDefault(language, Locale.FRANCE);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(javaLocale);

        String pattern = buildPattern(currency);
        DecimalFormat formatter = new DecimalFormat(pattern, symbols);

        BigDecimal rounded = round(amount, currency);
        return currency.getSymbol() + "\u00A0" + formatter.format(rounded);
    }

    /**
     * Builds the decimal format pattern based on currency conventions.
     * XAF and XOF do not use decimal fractions per ISO 4217.
     */
    private String buildPattern(SupportedCurrency currency) {
        return switch (currency) {
            case XAF, XOF -> "#,##0";
            default        -> "#,##0.00";
        };
    }

    /**
     * Rounds the amount to the appropriate scale for the given currency.
     */
    private BigDecimal round(BigDecimal amount, SupportedCurrency currency) {
        return switch (currency) {
            case XAF, XOF -> amount.setScale(0, RoundingMode.HALF_UP);
            default        -> amount.setScale(2, RoundingMode.HALF_UP);
        };
    }
}
