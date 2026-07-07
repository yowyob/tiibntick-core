package com.yowyob.tiibntick.core.resource.config;

import com.yowyob.tiibntick.core.resource.adapter.in.web.VehicleHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.PATCH;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@Configuration
public class VehicleRouterConfig {

    private static final String BASE = "/api/v1/resources/vehicles";

    @Bean
    public RouterFunction<ServerResponse> vehicleRoutes(VehicleHandler handler) {
        return RouterFunctions.route()
                .add(RouterFunctions.route(POST(BASE), handler::createVehicle))
                .add(RouterFunctions.route(GET(BASE + "/{id}"), handler::getVehicle))
                .add(RouterFunctions.route(GET("/api/v1/resources/agencies/{agencyId}/vehicles"), handler::listVehiclesByAgency))
                .add(RouterFunctions.route(POST(BASE + "/{id}/assign"), handler::assignVehicle))
                .add(RouterFunctions.route(DELETE(BASE + "/{id}/assign"), handler::unassignVehicle))
                .add(RouterFunctions.route(POST(BASE + "/{id}/maintenance"), handler::sendToMaintenance))
                .add(RouterFunctions.route(POST(BASE + "/{id}/maintenance/complete"), handler::completeMaintenance))
                .add(RouterFunctions.route(POST(BASE + "/{id}/retire"), handler::retireVehicle))
                .add(RouterFunctions.route(PATCH(BASE + "/{id}/odometer"), handler::updateOdometer))
                .add(RouterFunctions.route(PATCH(BASE + "/{id}/location"), handler::updateLocation))
                .add(RouterFunctions.route(POST("/api/v1/resources/vehicles/best-match"), handler::findBestVehicle))
                .add(RouterFunctions.route(GET("/api/v1/resources/agencies/{agencyId}/vehicles/maintenance-due"), handler::checkMaintenanceDue))
                .add(RouterFunctions.route(POST(BASE + "/{id}/maintenance-alert"), handler::scheduleMaintenanceAlert))
                .build();
    }
}
