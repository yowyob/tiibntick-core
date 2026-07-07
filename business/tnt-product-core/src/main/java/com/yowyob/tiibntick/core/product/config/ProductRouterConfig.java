package com.yowyob.tiibntick.core.product.config;

import com.yowyob.tiibntick.core.product.adapter.in.web.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@Configuration
public class ProductRouterConfig {

    private static final String PRODUCTS = "/api/v1/products";
    private static final String OFFERS = "/api/v1/service-offers";

    @Bean
    public RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
        return RouterFunctions.route()
                // Product CRUD
                .add(RouterFunctions.route(POST(PRODUCTS), handler::createProduct))
                .add(RouterFunctions.route(GET(PRODUCTS), handler::listProducts))
                .add(RouterFunctions.route(GET(PRODUCTS + "/{id}"), handler::getProduct))
                .add(RouterFunctions.route(POST(PRODUCTS + "/{id}/activate"), handler::activateProduct))
                .add(RouterFunctions.route(POST(PRODUCTS + "/{id}/archive"), handler::archiveProduct))
                // ServiceOffer
                .add(RouterFunctions.route(POST(OFFERS), handler::createServiceOffer))
                .add(RouterFunctions.route(GET(OFFERS + "/{id}"), handler::getServiceOffer))
                .add(RouterFunctions.route(GET("/api/v1/providers/{providerId}/service-offers"), handler::listOffersByProvider))
                .add(RouterFunctions.route(POST(OFFERS + "/{id}/publish"), handler::publishOffer))
                .add(RouterFunctions.route(DELETE(OFFERS + "/{id}/publish"), handler::unpublishOffer))
                .add(RouterFunctions.route(GET(OFFERS + "/matching"), handler::findMatchingOffers))
                .add(RouterFunctions.route(POST(OFFERS + "/compare"), handler::compareOffers))
                .build();
    }
}
