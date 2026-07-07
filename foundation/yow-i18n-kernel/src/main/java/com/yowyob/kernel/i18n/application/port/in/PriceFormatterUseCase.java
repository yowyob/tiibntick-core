package com.yowyob.kernel.i18n.application.port.in;

import com.yowyob.kernel.i18n.domain.enums.SupportedCurrency;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.kernel.i18n.domain.vo.LocalizedPrice;

import java.math.BigDecimal;

/**
 * Use case interface for formatting monetary amounts according to locale and currency rules.
 * Used by billing modules (tnt-billing-cost, tnt-billing-invoice) and the notification system.
 *
 * @author MANFOUO Braun
 */
public interface PriceFormatterUseCase {

    /**
     * Formats a monetary amount for a specific currency.
     *
     * @param amount the amount to format
     * @param currency  the target currency
     * @return a localized price value object with the formatted representation
     */
    LocalizedPrice format(BigDecimal amount, SupportedCurrency currency);

    /**
     * Formats a monetary amount for a specific language and currency pair.
     * The language influences the number grouping separator and decimal symbol.
     *
     * @param amount the amount to format
     * @param currency  the target currency
     * @param language  the display language
     * @return a localized price value object
     */
    LocalizedPrice formatForLocale(BigDecimal amount, SupportedCurrency currency, SupportedLanguage language);
}
