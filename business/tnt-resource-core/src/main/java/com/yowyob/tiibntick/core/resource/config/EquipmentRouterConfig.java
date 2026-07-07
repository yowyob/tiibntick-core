package com.yowyob.tiibntick.core.resource.config;

import com.yowyob.tiibntick.core.resource.adapter.in.web.EquipmentHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@Configuration
public class EquipmentRouterConfig {

    private static final String BASE = "/api/v1/resources/equipment";

    @Bean
    public RouterFunction<ServerResponse> equipmentRoutes(EquipmentHandler handler) {
        return RouterFunctions.route()
                .add(RouterFunctions.route(POST(BASE), handler::createEquipment))
                .add(RouterFunctions.route(GET(BASE + "/{id}"), handler::getEquipment))
                .add(RouterFunctions.route(GET("/api/v1/resources/branches/{branchId}/equipment"), handler::listEquipmentByBranch))
                .add(RouterFunctions.route(POST(BASE + "/{id}/assign"), handler::assignEquipment))
                .add(RouterFunctions.route(DELETE(BASE + "/{id}/assign"), handler::unassignEquipment))
                .build();
    }
}
