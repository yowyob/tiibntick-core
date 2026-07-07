package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

/**
 * Immutable contact information for a parcel recipient.
 *
 * @author MANFOUO Braun
 */
public record RecipientInfo(
        String name,
        String phoneNumber,
        String alternativePhone
) {

    public RecipientInfo {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipient name must not be blank");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Recipient phone number must not be blank");
        }
    }

    public static RecipientInfo of(String name, String phoneNumber) {
        return new RecipientInfo(name, phoneNumber, null);
    }
}
