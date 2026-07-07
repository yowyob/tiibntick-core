package com.yowyob.tiibntick.core.tp.domain.model.enums;

/**
 * Types of identity documents accepted for KYC verification.
 *
 * @author MANFOUO Braun
 */
public enum DocumentType {

    /** Cameroon National Identity Card (CNI). */
    NATIONAL_ID_CARD,

    /** International passport. */
    PASSPORT,

    /** Residence permit. */
    RESIDENCE_PERMIT,

    /** Driver's license. */
    DRIVERS_LICENSE,

    /** Business registration document (Registre du Commerce). */
    BUSINESS_REGISTRATION,

    /** Tax identification number (NIU for Cameroon). */
    TAX_IDENTIFICATION,

    /** Proof of address (utility bill, bank statement). */
    PROOF_OF_ADDRESS
}
