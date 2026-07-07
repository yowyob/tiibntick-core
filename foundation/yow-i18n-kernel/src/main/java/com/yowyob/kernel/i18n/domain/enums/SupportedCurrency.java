package com.yowyob.kernel.i18n.domain.enums;

/**
 * Currencies supported by the TiiBnTick platform across Africa.
 * Each currency carries its ISO code and display symbol.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public enum SupportedCurrency {

    XAF("XAF", "FCFA", "Franc CFA CEMAC"),   // Cameroon, Chad, Congo, CAR, Gabon, Eq. Guinea
    XOF("XOF", "FCFA", "Franc CFA UEMOA"),   // Senegal, Côte d'Ivoire, etc.
    NGN("NGN", "₦",    "Nigerian Naira"),
    KES("KES", "KSh",  "Kenyan Shilling"),
    USD("USD", "$",    "US Dollar"),
    EUR("EUR", "€",    "Euro");

    private final String isoCode;
    private final String symbol;
    private final String label;

    SupportedCurrency(String isoCode, String symbol, String label) {
        this.isoCode = isoCode;
        this.symbol = symbol;
        this.label = label;
    }

    public String getIsoCode()  { return isoCode;  }
    public String getSymbol()  { return symbol;  }
    public String getLabel()  { return label;  }
}
