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
}
