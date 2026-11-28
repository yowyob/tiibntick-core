package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

/**
 * Optional contact information of the party handing the parcel over, when it differs from the
 * announcing client's account (name, phone and e-mail all nullable).
 *
 * @author MANFOUO Braun
 */
public record SenderInfo(String name, String phoneNumber, String email) {

    public boolean isEmpty() {
        return isBlank(name) && isBlank(phoneNumber) && isBlank(email);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
