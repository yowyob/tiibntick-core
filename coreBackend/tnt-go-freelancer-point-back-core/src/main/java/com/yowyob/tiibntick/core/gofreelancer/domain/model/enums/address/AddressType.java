package com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address;

/**
 * Defines the type of address.
 * This enum is used to distinguish primary and secondary addresses.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
public enum AddressType {

    PRIMARY("PRIMARY"),
    SECONDARY("SECONDARY");

    private final String value;

    /**
     * Creates an AddressType enum.
     *
     * @param value database representation of the address type
     */
    AddressType(String value) {
        this.value = value;
    }

    /**
     * Returns the string value stored in the database.
     *
     * @return address type value
     */
    public String getValue() {
        return value;
    }

    /**
     * Converts a string value to an AddressType enum.
     *
     * @param value database value
     * @return matching AddressType
     * @throws IllegalArgumentException if the value is unknown
     */
    public static AddressType fromValue(String value) {
        for (AddressType type : AddressType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown address type: " + value);
    }
}