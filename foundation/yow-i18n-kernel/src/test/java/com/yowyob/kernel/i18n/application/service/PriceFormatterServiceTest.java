package com.yowyob.kernel.i18n.application.service;

import com.yowyob.kernel.i18n.application.port.out.PriceFormatterPort;
import com.yowyob.kernel.i18n.domain.enums.SupportedCurrency;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.kernel.i18n.domain.vo.LocalizedPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PriceFormatterService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class PriceFormatterServiceTest {

    @Mock
    private PriceFormatterPort priceFormatterPort;

    private PriceFormatterService service;

    @BeforeEach
    void setUp() {
        service = new PriceFormatterService(priceFormatterPort, SupportedLanguage.FR_CM);
    }

    @Test
    void format_shouldReturnLocalizedPrice_withDefaultLanguage() {
        // given
        BigDecimal amount = new BigDecimal("5000");
        when(priceFormatterPort.formatAmount(amount, SupportedCurrency.XAF, SupportedLanguage.FR_CM))
                .thenReturn("FCFA 5 000");

        // when
        LocalizedPrice result = service.format(amount, SupportedCurrency.XAF);

        // then
        assertThat(result.currency()).isEqualTo(SupportedCurrency.XAF);
        assertThat(result.amount()).isEqualByComparingTo(amount);
        assertThat(result.representation()).isEqualTo("FCFA 5 000");
    }

    @Test
    void formatForLocale_shouldUseSpecifiedLanguage() {
        // given
        BigDecimal amount = new BigDecimal("3500");
        when(priceFormatterPort.formatAmount(amount, SupportedCurrency.NGN, SupportedLanguage.EN_NG))
                .thenReturn("₦ 3,500.00");

        // when
        LocalizedPrice result = service.formatForLocale(amount, SupportedCurrency.NGN, SupportedLanguage.EN_NG);

        // then
        assertThat(result.representation()).isEqualTo("₦ 3,500.00");
    }

    @Test
    void format_shouldThrowNullPointerException_whenAmountIsNull() {
        assertThatThrownBy(() -> service.format(null, SupportedCurrency.XAF))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldThrow_whenPortIsNull() {
        assertThatThrownBy(() -> new PriceFormatterService(null, SupportedLanguage.FR_CM))
                .isInstanceOf(NullPointerException.class);
    }
}
