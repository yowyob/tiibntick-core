package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating an address.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressCreateRequest {
    private String street;
    private String city;
    private String country;
    private String postalCode;
    private AddressType type;
    private Double latitude;
    private Double longitude;
}
