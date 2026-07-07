package com.yowyob.tiibntick.core.organization.domain.enums;

/**
 * Know-Your-Customer verification level for a FreelancerOrganization.
 *
 * <p>Each level unlocks additional mission scopes:
 * <ul>
 *   <li>{@code NONE}  — observation only, no missions assignable.</li>
 *   <li>{@code BASIC} — missions &lt; 10 km, parcels &lt; 5 kg, amount &lt; 5000 XAF.</li>
 *   <li>{@code FULL}  — all missions within declared capabilities.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum KycLevel {

    /** No identity verification performed. Zero mission access. */
    NONE,

    /** Basic verification: national ID photo + selfie uploaded and validated. */
    BASIC,

    /**
     * Full verification: CNI/Passport + vehicle registration +
     * insurance document + optional criminal record check.
     */
    FULL
}
