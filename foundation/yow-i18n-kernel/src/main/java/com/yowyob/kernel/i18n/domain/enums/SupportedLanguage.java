package com.yowyob.kernel.i18n.domain.enums;

/**
 * Languages supported by TiiBnTick, with African regional variants prioritized.
 * The locale tag matches the JSON message file naming convention:
 * messages_{tag}.json (e.g., messages_fr_CM.json).
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public enum SupportedLanguage {

    FR_CM("fr_CM",   "Français Cameroun"),
    EN_CM("en_CM",   "English Cameroon"),
    PIDGIN_CM("pidgin_CM", "Cameroon Pidgin English"),
    EN_NG("en_NG",   "English Nigeria"),
    FR_FR("fr_FR",   "Français Standard"),
    EN_US("en_US",   "American English");

    private final String tag;
    private final String label;

    SupportedLanguage(String tag, String label) {
        this.tag     = tag;
        this.label = label;
    }

    public String getTag()     { return tag;     }
    public String getLabel() { return label; }

    /**
     * Resolves a free-form language code to the pack-indexed {@link SupportedLanguage}
     * it maps to, so callers can normalize whatever they receive (often a bare ISO
     * code such as {@code "fr"} or {@code "en"}) to the {@code fr_CM}/{@code fr_FR}/...
     * tag that {@code messages_{tag}.json} lookups actually key on.
     *
     * <p>Matching order:
     * <ol>
     *   <li>Exact tag match, case-insensitive (e.g. {@code "fr_CM"}, {@code "en-us"})</li>
     *   <li>Primary language subtag match — the part before the first {@code _}/{@code -}
     *       (e.g. {@code "fr"} matches {@link #FR_CM}, the first {@code FR_*} constant
     *       declared), so African-first defaults win ties</li>
     * </ol>
     *
     * @param code free-form language code; may be {@code null} or blank
     * @return the resolved constant, or {@code null} if nothing matches
     */
    public static SupportedLanguage fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().replace('-', '_');
        for (SupportedLanguage lang : values()) {
            if (lang.tag.equalsIgnoreCase(normalized)) {
                return lang;
            }
        }
        String primary = normalized.split("_")[0];
        for (SupportedLanguage lang : values()) {
            String langPrimary = lang.tag.split("_")[0];
            if (langPrimary.equalsIgnoreCase(primary)) {
                return lang;
            }
        }
        return null;
    }
}
