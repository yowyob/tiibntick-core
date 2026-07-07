package com.yowyob.tiibntick.core.accounting.domain.model;

/**
 * OHADA chart-of-accounts classes (1 to 9).
 * Each class groups semantically related accounts as per the
 * Organisation pour l'Harmonisation en Afrique du Droit des Affaires standard.
 * Author: MANFOUO Braun
 */
public enum AccountCategory {

    /** Class 1 — Comptes de ressources durables (equity, long-term debt). */
    CLASS_1_EQUITY_LONG_TERM_DEBT(1, "Comptes de ressources durables"),

    /** Class 2 — Comptes d'actif immobilisé (fixed assets). */
    CLASS_2_FIXED_ASSETS(2, "Comptes d'actif immobilisé"),

    /** Class 3 — Comptes de stocks (inventory). */
    CLASS_3_INVENTORY(3, "Comptes de stocks"),

    /** Class 4 — Comptes de tiers (third-party / AR / AP). */
    CLASS_4_THIRD_PARTY(4, "Comptes de tiers"),

    /** Class 5 — Comptes de trésorerie (cash & financial accounts). */
    CLASS_5_FINANCIAL(5, "Comptes de trésorerie"),

    /** Class 6 — Comptes de charges des activités ordinaires (expenses). */
    CLASS_6_EXPENSES(6, "Comptes de charges des activités ordinaires"),

    /** Class 7 — Comptes de produits des activités ordinaires (revenues). */
    CLASS_7_REVENUES(7, "Comptes de produits des activités ordinaires"),

    /** Class 8 — Comptes des autres charges et des autres produits. */
    CLASS_8_SPECIAL(8, "Comptes des autres charges et produits"),

    /** Class 9 — Comptes de la comptabilité analytique de gestion. */
    CLASS_9_ANALYTICAL(9, "Comptes de comptabilité analytique");

    private final int classNumber;
    private final String label;

    AccountCategory(int classNumber, String label) {
        this.classNumber = classNumber;
        this.label = label;
    }

    public int getClassNumber() {
        return classNumber;
    }

    public String getLabel() {
        return label;
    }

    public static AccountCategory fromClassNumber(int number) {
        for (AccountCategory cat : values()) {
            if (cat.classNumber == number) {
                return cat;
            }
        }
        throw new IllegalArgumentException("Unknown OHADA class number: " + number);
    }

    public static AccountCategory fromAccountCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Account code must not be null or blank");
        }
        int classNum = Character.getNumericValue(code.charAt(0));
        return fromClassNumber(classNum);
    }
}
