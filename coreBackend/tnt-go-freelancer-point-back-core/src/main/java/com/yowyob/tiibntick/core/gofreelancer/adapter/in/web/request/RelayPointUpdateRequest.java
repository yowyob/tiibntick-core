package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayPointUpdateRequest {
    private String name;
    private String storefrontPhoto;
    private String shopPhoto;
    private UUID addressId;
    private Boolean isActive;

    // ── Storage space dimensions ──────────────────────────────────────────
    /** Length of the hangar/storage room in the unit defined by storageDimensionUnit. */
    private Double storageLength;
    private Double storageWidth;
    private Double storageHeight;
    /** Unit for storage dimensions: "m", "cm", "mm", "in". Defaults to "m" when absent. */
    private String storageDimensionUnit;
}
