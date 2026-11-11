package com.yowyob.kernel.i18n.domain.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normalisation and fallback rules for language codes.
 *
 * <p>TiiBnTick exposes two <b>public</b> codes — {@code fr} and {@code en} — that every platform
 * (Go-Freelancer, Link, Agency, Point, Market) stores and sends. Regional tags such as
 * {@code fr_CM}, {@code en-US} or {@code pidgin_CM} are still understood: they resolve to a
 * <em>fallback chain</em> (most specific first) so a regional override wins when it exists and the
 * base language is used otherwise. Adding a third language later only means adding it to the
 * supported set; nothing else in the resolution logic is language-specific.
 */
public final class LanguageTags {

    /** Public codes accepted by the APIs and stored in user preferences. */
    public static final String FRENCH = "fr";
    public static final String ENGLISH = "en";
    public static final String DEFAULT = FRENCH;

    /** Languages the platform ships messages for. Regional/pidgin tags fold into one of these. */
    private static final List<String> SUPPORTED = List.of(FRENCH, ENGLISH);

    private LanguageTags() {
    }

    /** The public language codes in display order. */
    public static List<String> supported() {
        return SUPPORTED;
    }

    /** True when {@code code} is exactly one of the public codes (after trimming and lower-casing). */
    public static boolean isSupported(String code) {
        return code != null && SUPPORTED.contains(code.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Folds any tag to its public language: {@code "fr_CM"} → {@code "fr"}, {@code "en-US"} →
     * {@code "en"}, {@code "pidgin_CM"} → {@code "en"} (an English creole, closest shipped language).
     *
     * @return the public code, or {@code null} when nothing sensible matches
     */
    public static String toPublicCode(String tag) {
        String normalized = normalize(tag);
        if (normalized == null) {
            return null;
        }
        String primary = primary(normalized);
        if (SUPPORTED.contains(primary)) {
            return primary;
        }
        return "pidgin".equals(primary) ? ENGLISH : null;
    }

    /** Public code for {@code tag}, or the platform default when it cannot be resolved. */
    public static String toPublicCodeOrDefault(String tag) {
        String code = toPublicCode(tag);
        return code != null ? code : DEFAULT;
    }

    /**
     * Picks the best supported language from an {@code Accept-Language} header value, honouring
     * quality weights ({@code "en-US,en;q=0.9,fr;q=0.8"}). Returns {@code null} when no listed range
     * maps to a supported language.
     */
    public static String fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String best = null;
        double bestQ = -1;
        for (String part : header.split(",")) {
            String[] pieces = part.trim().split(";");
            String code = toPublicCode(pieces[0]);
            if (code == null) {
                continue;
            }
            double q = 1.0;
            for (int i = 1; i < pieces.length; i++) {
                String p = pieces[i].trim();
                if (p.startsWith("q=")) {
                    try {
                        q = Double.parseDouble(p.substring(2));
                    } catch (NumberFormatException ignored) {
                        q = 0;
                    }
                }
            }
            if (q > bestQ) {
                bestQ = q;
                best = code;
            }
        }
        return best;
    }

    /**
     * Lookup order for a message: the exact tag (a regional override), then its public language,
     * then the platform default's tag and public language. Duplicates are removed.
     *
     * <p>{@code chain("en_CM")} → {@code [en_CM, en, fr]}; {@code chain("xx")} → {@code [fr]}.
     */
    public static List<String> fallbackChain(String requested) {
        List<String> chain = new ArrayList<>(3);
        String normalized = normalize(requested);
        if (normalized != null) {
            chain.add(normalized);
            String publicCode = toPublicCode(normalized);
            if (publicCode != null && !chain.contains(publicCode)) {
                chain.add(publicCode);
            }
        }
        if (!chain.contains(DEFAULT)) {
            chain.add(DEFAULT);
        }
        return List.copyOf(chain);
    }

    /** {@code "EN-us"} → {@code "en_US"}; {@code "fr"} → {@code "fr"}; blank → {@code null}. */
    public static String normalize(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String[] parts = tag.trim().replace('-', '_').split("_");
        String language = parts[0].toLowerCase(Locale.ROOT);
        if (parts.length == 1) {
            return language;
        }
        return language + "_" + parts[1].toUpperCase(Locale.ROOT);
    }

    /** The {@link Locale} used for number/date formatting of a language (regional tag honoured). */
    public static Locale toLocale(String tag) {
        String normalized = normalize(tag);
        if (normalized == null) {
            return Locale.FRENCH;
        }
        String primary = primary(normalized);
        if ("pidgin".equals(primary)) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag(normalized.replace('_', '-'));
    }

    private static String primary(String normalizedTag) {
        int i = normalizedTag.indexOf('_');
        return i < 0 ? normalizedTag : normalizedTag.substring(0, i);
    }
}
