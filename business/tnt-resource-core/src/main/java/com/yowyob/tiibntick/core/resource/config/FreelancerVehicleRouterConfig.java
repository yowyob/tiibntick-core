package com.yowyob.tiibntick.core.resource.config;

import com.yowyob.tiibntick.core.resource.adapter.in.web.FreelancerVehicleHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * Router configuration for FreelancerOrg fleet endpoints.
 *
 * <p>Base path: {@code /api/v1/resources/freelancer-orgs/{orgId}/fleet}
 *
 * <p>Endpoint summary:
 * <ul>
 *   <li>POST   /fleet/vehicles              — Add vehicle to org fleet</li>
 *   <li>GET    /fleet/vehicles              — List org fleet vehicles</li>
 *   <li>GET    /fleet/vehicles/{vehicleId}  — Get single vehicle</li>
 *   <li>POST   /fleet/vehicles/{vehicleId}/assign-mission  — Assign to mission</li>
 *   <li>POST   /fleet/vehicles/{vehicleId}/release-mission — Release from mission</li>
 *   <li>POST   /fleet/vehicles/{vehicleId}/deactivate      — Deactivate vehicle</li>
 *   <li>POST   /fleet/equipments            — Add equipment to org</li>
 *   <li>GET    /fleet/equipments            — List org active equipments</li>
 *   <li>GET    /fleet/equipments/has-type   — Check equipment type availability</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Configuration
public class FreelancerVehicleRouterConfig {

    private static final String BASE = "/api/v1/resources/freelancer-orgs/{orgId}/fleet";

    @Bean
    public RouterFunction<ServerResponse> freelancerFleetRoutes(FreelancerVehicleHandler handler) {
        return RouterFunctions.route()
                // Vehicle routes
                .add(RouterFunctions.route(POST(BASE + "/vehicles"),              handler::addVehicle))
                .add(RouterFunctions.route(GET(BASE + "/vehicles"),               handler::listVehicles))
                .add(RouterFunctions.route(GET(BASE + "/vehicles/{vehicleId}"),   handler::getVehicle))
                .add(RouterFunctions.route(POST(BASE + "/vehicles/{vehicleId}/assign-mission"),  handler::assignToMission))
                .add(RouterFunctions.route(POST(BASE + "/vehicles/{vehicleId}/release-mission"), handler::releaseFromMission))
                .add(RouterFunctions.route(POST(BASE + "/vehicles/{vehicleId}/deactivate"),      handler::deactivateVehicle))
                // Equipment routes
                .add(RouterFunctions.route(POST(BASE + "/equipments"),           handler::addEquipment))
                .add(RouterFunctions.route(GET(BASE + "/equipments"),            handler::listEquipments))
                .add(RouterFunctions.route(GET(BASE + "/equipments/has-type"),   handler::hasEquipmentType))
                .build();
    }
}
