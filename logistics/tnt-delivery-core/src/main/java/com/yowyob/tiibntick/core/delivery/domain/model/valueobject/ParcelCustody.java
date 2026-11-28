package com.yowyob.tiibntick.core.delivery.domain.model.valueobject;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.CustodyHolderType;

import java.util.Objects;
import java.util.UUID;

/**
 * Who physically holds a parcel (spec §1.2 {@code custody}): the sender before pickup, a courier, a relay hub (with
 * its inventory entry), the recipient once delivered.
 *
 * @param holderType        kind of holder
 * @param holderId          actor id of the holder (courier, sender, recipient), {@code null} when unknown or a relay
 * @param hubId             relay hub holding the parcel ({@code RELAY} only)
 * @param hubPackageEntryId inventory entry of the deposit at the hub ({@code RELAY} only)
 */
public record ParcelCustody(CustodyHolderType holderType, UUID holderId, UUID hubId, UUID hubPackageEntryId) {

    public ParcelCustody {
        Objects.requireNonNull(holderType, "holderType");
    }

    public static ParcelCustody sender(UUID senderId) {
        return new ParcelCustody(CustodyHolderType.SENDER, senderId, null, null);
    }

    public static ParcelCustody courier(UUID courierId) {
        return new ParcelCustody(CustodyHolderType.COURIER, courierId, null, null);
    }

    public static ParcelCustody relay(UUID hubId, UUID hubPackageEntryId) {
        return new ParcelCustody(CustodyHolderType.RELAY, null, hubId, hubPackageEntryId);
    }

    public static ParcelCustody recipient(UUID recipientId) {
        return new ParcelCustody(CustodyHolderType.RECIPIENT, recipientId, null, null);
    }

    public static ParcelCustody none() {
        return new ParcelCustody(CustodyHolderType.NONE, null, null, null);
    }
}
