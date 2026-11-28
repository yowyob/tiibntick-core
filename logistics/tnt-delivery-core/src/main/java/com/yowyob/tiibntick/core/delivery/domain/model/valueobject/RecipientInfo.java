package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Immutable contact information for a parcel recipient.
 *
 * <p>{@code email} is optional and absent from the JSON form when null, so existing consumers of
 * events carrying a recipient see no change.
 *
 * @author MANFOUO Braun
 */
public record RecipientInfo(
        String name,
        String phoneNumber,
        String alternativePhone,
        @JsonInclude(JsonInclude.Include.NON_NULL) String email
) {

    public RecipientInfo {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipient name must not be blank");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Recipient phone number must not be blank");
        }
    }

    /** Backward-compatible constructor: no e-mail. */
    public RecipientInfo(String name, String phoneNumber, String alternativePhone) {
        this(name, phoneNumber, alternativePhone, null);
    }

    public static RecipientInfo of(String name, String phoneNumber) {
        return new RecipientInfo(name, phoneNumber, null, null);
    }

    public static RecipientInfo of(String name, String phoneNumber, String email) {
        return new RecipientInfo(name, phoneNumber, null, email);
    }
}
