package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/** Who physically holds a parcel (spec §1.2 {@code custody.holderType}). */
public enum CustodyHolderType {
    SENDER, COURIER, RELAY, RECIPIENT, NONE
}
