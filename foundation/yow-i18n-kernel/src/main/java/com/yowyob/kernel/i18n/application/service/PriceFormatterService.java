package com.yowyob.kernel.i18n.application.service;

import com.yowyob.kernel.i18n.application.port.in.PriceFormatterUseCase;
import com.yowyob.kernel.i18n.application.port.out.PriceFormatterPort;
import com.yowyob.kernel.i18n.domain.enums.SupportedCurrency;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.kernel.i18n.domain.vo.LocalizedPrice;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Application service implementing price formatting for African locales.
 * Pure domain service — no Spring annotations, injectable via AutoConfiguration.
 *
 * @author MANFOUO Braun
 */
public class PriceFormatterService implements PriceFormatterUseCase {

    private final PriceFormatterPort priceFormatterPort;

    /** Default language used when none is specified. */
    private final SupportedLanguage defaultLanguage;

    public PriceFormatterService(PriceFormatterPort priceFormatterPort, SupportedLanguage defaultLanguage) {
        this.priceFormatterPort  = Objects.requireNonNull(priceFormatterPort, "PriceFormatterPort must not be null");
        this.defaultLanguage   = Objects.requireNonNull(defaultLanguage, "Default language must not be null");
    }

    @Override
    public LocalizedPrice format(BigDecimal amount, SupportedCurrency currency) {
        return formatForLocale(amount, currency, defaultLanguage);
    }

    @Override
    public LocalizedPrice formatForLocale(BigDecimal amount, SupportedCurrency currency, SupportedLanguage language) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency,  "Currency must not be null");
        Objects.requireNonNull(language,  "Language must not be null");

        String representation = priceFormatterPort.formatAmount(amount, currency, language);
        return new LocalizedPrice(amount, currency, representation);
    }
}
