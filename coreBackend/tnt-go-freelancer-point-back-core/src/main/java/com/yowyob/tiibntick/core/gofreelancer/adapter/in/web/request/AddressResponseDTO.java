package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for address response.
 * Contains complete address information for GET operations.
 *
 * @author Kengfack Lagrange
 * @date 17/12/2025
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressResponseDTO {

    private UUID id;
    private String street;
    private String city;
    private String district;
    private String country;
    private String description;


    /**
     * Get formatted full address
     */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (street != null) sb.append(street).append(", ");
        if (district != null) sb.append(district).append(", ");
        if (city != null) sb.append(city).append(", ");
        if (country != null) sb.append(country);

        String result = sb.toString();
        // Remove trailing comma if present
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        return result;
    }
}