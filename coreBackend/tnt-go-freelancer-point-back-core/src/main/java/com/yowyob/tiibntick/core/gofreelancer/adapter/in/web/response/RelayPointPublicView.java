package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import java.util.UUID;

/**
 * A relay point as a sender sees it when choosing where to send a parcel: identity, place and
 * storage only. No owner identity or contact (name, email, phone) and no owner profile.
 *
 * @param id               gofp relay point id
 * @param coreRelayPointId hub id — the id a delivery deposit and a delivery need target
 */
public record RelayPointPublicView(
        UUID id,
        UUID coreRelayPointId,
        String name,
        Double rating,
        String street,
        String quarter,
        String city,
        String landmark,
        Double latitude,
        Double longitude,
        Double storageLength,
        Double storageWidth,
        Double storageHeight,
        String storageDimensionUnit) {
}
