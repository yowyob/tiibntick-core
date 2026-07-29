package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for triggering a manual reroute of a delivery in transit.
 * The freelancerId is resolved automatically from the Delivery entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualRerouteRequestDTO {

    /** The new route ID computed by tnt-route-core. */
    private String newRouteId;

    /** Human-readable reason for the reroute (e.g., "Route bloquée", "Client a changé d'adresse"). */
    private String reason;
}
