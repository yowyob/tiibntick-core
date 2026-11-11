package com.yowyob.kernel.i18n.domain.format;

import com.yowyob.kernel.i18n.domain.vo.LanguageTags;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a message template for one recipient and one language.
 *
 * <h3>Syntax</h3>
 * <pre>
 *   {{name}}                                   plain value ("" when the parameter is null)
 *   {{amount:money}}   {{amount:money|XAF}}    amount with currency (parameter "currency" or the arg)
 *   {{n:number}}                               locale-grouped number
 *   {{when:date}} {{when:time}} {{when:datetime}}   localised date/time (Africa/Douala unless "zone" is set)
 *   {{km:distance}}                            kilometres; below 1 km shown in metres
 *   {{count:plural|one=# parcel|other=# parcels}}   plural selection, "#" is the number
 * </pre>
 * A parameter that is absent from the map is left as written so a missing value is visible in tests
 * instead of silently producing a wrong sentence. Rendering never throws on bad input: a malformed
 * token is emitted verbatim.
 *
 * <p>Instances are stateless and thread-safe.
 */
public final class MessageFormatter {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Douala");
    private static final String DEFAULT_CURRENCY = "XAF";

    private MessageFormatter() {
    }

    /**
     * @param template   the stored text, possibly {@code null}
     * @param language   any tag ({@code fr}, {@code en}, {@code en_CM}…); drives numbers, dates and plural rules
     * @param parameters values by name, possibly {@code null}
     */
    public static String format(String template, String language, Map<String, ?> parameters) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Map<String, ?> params = parameters != null ? parameters : Map.of();
        Locale locale = LanguageTags.toLocale(language);
        StringBuilder out = new StringBuilder(template.length() + 16);
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf("{{", i);
            if (open < 0) {
                out.append(template, i, template.length());
                break;
            }
            int close = template.indexOf("}}", open + 2);
            if (close < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, open);
            out.append(renderToken(template.substring(open + 2, close), template.substring(open, close + 2),
                    locale, params));
            i = close + 2;
        }
        return out.toString();
    }

    /** True when the template still holds a {@code {{token}}} after rendering (a parameter was missing). */
    public static boolean hasUnresolved(String rendered) {
        if (rendered == null) {
            return false;
        }
        int open = rendered.indexOf("{{");
        return open >= 0 && rendered.indexOf("}}", open) > open;
    }

    // ── token ──────────────────────────────────────────────────────────────

    private static String renderToken(String body, String verbatim, Locale locale, Map<String, ?> params) {
        try {
            String spec = body;
            String args = null;
            int bar = spec.indexOf('|');
            if (bar >= 0) {
                args = spec.substring(bar + 1);
                spec = spec.substring(0, bar);
            }
            String name = spec;
            String format = null;
            int colon = spec.indexOf(':');
            if (colon >= 0) {
                name = spec.substring(0, colon);
                format = spec.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
            }
            name = name.trim();
            if (name.isEmpty() || !params.containsKey(name)) {
                return verbatim;
            }
            Object value = params.get(name);
            if (value == null) {
                return "";
            }
            if (format == null || format.isEmpty()) {
                return String.valueOf(value);
            }
            return switch (format) {
                case "plural" -> plural(value, args, locale);
                case "money" -> money(value, args, locale, params);
                case "number" -> number(value, locale);
                case "date" -> temporal(value, locale, params, FormatStyle.MEDIUM, null);
                case "time" -> temporal(value, locale, params, null, FormatStyle.SHORT);
                case "datetime" -> temporal(value, locale, params, FormatStyle.MEDIUM, FormatStyle.SHORT);
                case "distance" -> distance(value, locale);
                default -> String.valueOf(value);
            };
        } catch (RuntimeException e) {
            return verbatim;
        }
    }

    // ── formats ────────────────────────────────────────────────────────────

    private static String plural(Object value, String args, Locale locale) {
        BigDecimal n = toDecimal(value);
        Map<String, String> cases = new LinkedHashMap<>();
        if (args != null) {
            for (String c : args.split("\\|")) {
                int eq = c.indexOf('=');
                if (eq > 0) {
                    cases.put(c.substring(0, eq).trim().toLowerCase(Locale.ROOT), c.substring(eq + 1));
                }
            }
        }
        String category = pluralCategory(n, locale);
        String chosen = null;
        if (n.signum() == 0 && cases.containsKey("zero")) {
            chosen = cases.get("zero");
        }
        if (chosen == null) {
            chosen = cases.getOrDefault(category, cases.get("other"));
        }
        if (chosen == null) {
            return number(value, locale);
        }
        return chosen.replace("#", number(value, locale));
    }

    /** CLDR-style: French treats 0 and 1 as singular; English only 1. */
    static String pluralCategory(BigDecimal n, Locale locale) {
        boolean integer = n.stripTrailingZeros().scale() <= 0;
        if ("fr".equals(locale.getLanguage())) {
            return n.compareTo(BigDecimal.ZERO) >= 0 && n.compareTo(BigDecimal.valueOf(2)) < 0 ? "one" : "other";
        }
        return integer && n.compareTo(BigDecimal.ONE) == 0 ? "one" : "other";
    }

    private static String money(Object value, String args, Locale locale, Map<String, ?> params) {
        BigDecimal amount = toDecimal(value);
        Object paramCurrency = params.get("currency");
        String currency = args != null && !args.isBlank()
                ? args.trim().toUpperCase(Locale.ROOT)
                : (paramCurrency != null ? String.valueOf(paramCurrency) : DEFAULT_CURRENCY).toUpperCase(Locale.ROOT);
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        boolean zeroDecimal = "XAF".equals(currency) || "XOF".equals(currency);
        nf.setMinimumFractionDigits(zeroDecimal ? 0 : (amount.scale() > 0 ? 2 : 0));
        nf.setMaximumFractionDigits(zeroDecimal ? 0 : 2);
        String digits = nf.format(amount);
        // French writes the unit after the number, English before it.
        return "fr".equals(locale.getLanguage()) ? digits + " " + currency : currency + " " + digits;
    }

    private static String number(Object value, Locale locale) {
        BigDecimal n = toDecimal(value);
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        nf.setMaximumFractionDigits(n.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return nf.format(n);
    }

    private static String distance(Object value, Locale locale) {
        BigDecimal km = toDecimal(value);
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        if (km.abs().compareTo(BigDecimal.ONE) < 0) {
            nf.setMaximumFractionDigits(0);
            return nf.format(km.multiply(BigDecimal.valueOf(1000))) + " m";
        }
        nf.setMaximumFractionDigits(1);
        return nf.format(km) + " km";
    }

    private static String temporal(Object value, Locale locale, Map<String, ?> params,
                                   FormatStyle dateStyle, FormatStyle timeStyle) {
        ZoneId zone = zoneOf(params);
        TemporalAccessor t = toTemporal(value, zone);
        DateTimeFormatter f;
        if (dateStyle != null && timeStyle != null) {
            f = DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle);
        } else if (dateStyle != null) {
            f = DateTimeFormatter.ofLocalizedDate(dateStyle);
        } else {
            f = DateTimeFormatter.ofLocalizedTime(timeStyle);
        }
        return f.withLocale(locale).format(t);
    }

    // ── conversions ────────────────────────────────────────────────────────

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(String.valueOf(value).trim());
    }

    private static ZoneId zoneOf(Map<String, ?> params) {
        Object z = params.get("zone");
        if (z != null) {
            try {
                return ZoneId.of(String.valueOf(z));
            } catch (RuntimeException ignored) {
                // fall through to the platform zone
            }
        }
        return DEFAULT_ZONE;
    }

    private static TemporalAccessor toTemporal(Object value, ZoneId zone) {
        if (value instanceof Instant i) {
            return i.atZone(zone);
        }
        if (value instanceof ZonedDateTime z) {
            return z.withZoneSameInstant(zone);
        }
        if (value instanceof OffsetDateTime o) {
            return o.atZoneSameInstant(zone);
        }
        if (value instanceof LocalDateTime || value instanceof LocalDate || value instanceof LocalTime) {
            return (TemporalAccessor) value;
        }
        if (value instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue()).atZone(zone);
        }
        String s = String.valueOf(value).trim();
        try {
            return Instant.parse(s).atZone(zone);
        } catch (RuntimeException e1) {
            try {
                return OffsetDateTime.parse(s).atZoneSameInstant(zone);
            } catch (RuntimeException e2) {
                try {
                    return LocalDateTime.parse(s);
                } catch (RuntimeException e3) {
                    return LocalDate.parse(s);
                }
            }
        }
    }
}
