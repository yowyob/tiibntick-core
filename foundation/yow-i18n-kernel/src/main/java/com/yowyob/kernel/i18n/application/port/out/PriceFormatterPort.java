package com.yowyob.kernel.i18n.application.port.out;

import com.yowyob.kernel.i18n.domain.enums.SupportedCurrency;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;

import java.math.BigDecimal;

/**
 * Secondary port (driven) for currency formatting operations.
 * Isolates the domain from specific formatting frameworks or APIs.
 *
 * @author MANFOUO Braun
 */
public interface PriceFormatterPort {

    /**
     * Formats a price amount into a locale-aware string representation.
     *
     * @param amount the monetary amount
     * @param currency  the currency to use
     * @param language  the language influencing grouping/decimal symbols
     * @return the formatted string (e.g., "FCFA 15 000" or "₦ 3,500.00")
     */
    String formatAmount(BigDecimal amount, SupportedCurrency currency, SupportedLanguage language);
}
