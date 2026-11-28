package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Address as the Go-Freelancer services read it: a plain read model of the core address
 * ({@code tnt-geo-core}, {@code IManageAddressUseCase}). It is <strong>not</strong> persisted by this module any
 * more (the former {@code addresses} table was migrated to {@code tnt_geography.tnt_addresses} with the same ids);
 * the name is kept so that the services that read addresses by id keep compiling unchanged.
 *
 * <p>{@code landmark} is the core's landmark, {@code quarter} the core's district.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressEntity {

    private UUID id;

    /** Usage label of the address ("PRIMARY", "HOME"...): the label of its link, not a property of the address. */
    private String type;

    private String street;
    private String landmark;
    private String quarter;
    private String city;
    private String region;
    private String country;
    private String postalCode;
    private Double latitude;
    private Double longitude;
}
