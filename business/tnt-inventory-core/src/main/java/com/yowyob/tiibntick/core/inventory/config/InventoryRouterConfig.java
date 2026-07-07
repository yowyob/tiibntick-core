package com.yowyob.tiibntick.core.inventory.config;
import com.yowyob.tiibntick.core.inventory.adapter.in.web.InventoryHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.*;
@Configuration
public class InventoryRouterConfig {
    @Bean
    public RouterFunction<ServerResponse> inventoryRoutes(InventoryHandler handler) {
        return RouterFunctions.route()
                .add(RouterFunctions.route(POST("/api/v1/inventory/stock/receive"), handler::receiveStock))
                .add(RouterFunctions.route(POST("/api/v1/inventory/stock/reserve"), handler::reserveStock))
                .add(RouterFunctions.route(GET("/api/v1/inventory/stock"), handler::getStockEntry))
                .add(RouterFunctions.route(GET("/api/v1/inventory/alerts/low-stock"), handler::getLowStockAlerts))
                .add(RouterFunctions.route(POST("/api/v1/inventory/hubs/{hubId}/packages"), handler::depositHubPackage))
                .add(RouterFunctions.route(POST("/api/v1/inventory/hub-packages/{trackingCode}/pickup"), handler::pickupHubPackage))
                .add(RouterFunctions.route(GET("/api/v1/inventory/hubs/{hubId}/occupancy"), handler::getHubOccupancy))
                .add(RouterFunctions.route(GET("/api/v1/inventory/hubs/{hubId}/overdue"), handler::findOverduePackages))
                .build();
    }
}
