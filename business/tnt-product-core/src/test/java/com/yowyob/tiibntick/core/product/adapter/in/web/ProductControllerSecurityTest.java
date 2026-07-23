package com.yowyob.tiibntick.core.product.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.product.application.port.in.ActivateProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.ArchiveProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.CreateProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.GetProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.ListProductsByTenantUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;

/**
 * Regression test for Audit n°7 · #6 — {@code ProductController}'s mutation endpoints
 * (create/activate/archive/delete) had zero {@code @PreAuthorize}/{@code @RequirePermission}
 * guard, so any caller reaching the controller (regardless of the outer gateway/JWT chain
 * configuration) could mutate a tenant's product catalog with no authorization check at all.
 *
 * <p>Wires a minimal Spring context with {@code @EnableReactiveMethodSecurity} (the same
 * mechanism {@code tnt-bootstrap}'s {@code TntSecurityConfig} enables app-wide) around the
 * real controller bean, with no {@code Authentication} in the reactive security context —
 * an anonymous caller. Before the {@code @PreAuthorize("isAuthenticated()")} fix, this call
 * reached the use case with no check at all; after the fix, the method-security interceptor
 * rejects it before the controller body ever runs.
 *
 * @author MANFOUO Braun
 */
@SpringJUnitConfig(classes = ProductControllerSecurityTest.TestConfig.class)
class ProductControllerSecurityTest {

    @Configuration
    @EnableReactiveMethodSecurity
    static class TestConfig {
        @Bean
        CreateProductUseCase createProductUseCase() {
            return mock(CreateProductUseCase.class);
        }

        @Bean
        GetProductUseCase getProductUseCase() {
            return mock(GetProductUseCase.class);
        }

        @Bean
        ListProductsByTenantUseCase listProductsByTenantUseCase() {
            return mock(ListProductsByTenantUseCase.class);
        }

        @Bean
        ActivateProductUseCase activateProductUseCase() {
            return mock(ActivateProductUseCase.class);
        }

        @Bean
        ArchiveProductUseCase archiveProductUseCase() {
            return mock(ArchiveProductUseCase.class);
        }

        @Bean
        ProductController productController(
                CreateProductUseCase createProductUseCase,
                GetProductUseCase getProductUseCase,
                ListProductsByTenantUseCase listProductsUseCase,
                ActivateProductUseCase activateProductUseCase,
                ArchiveProductUseCase archiveProductUseCase) {
            return new ProductController(createProductUseCase, getProductUseCase,
                    listProductsUseCase, activateProductUseCase, archiveProductUseCase);
        }
    }

    @Autowired
    private ProductController controller;

    @Test
    void createProduct_anonymousCaller_isDeniedBeforeReachingUseCase() {
        TntUserIdentity currentUser = new TntUserIdentity(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null, Set.of(), false);
        ProductController.CreateProductRequest request = new ProductController.CreateProductRequest(
                "SKU-1", "Test Product", "desc", null, null, "GOOD",
                1000.0, "XAF", "UNIT", 1.0, List.of(), Map.of());

        // No Authentication in the reactive context — an anonymous caller reaching the
        // controller directly (whatever the outer gateway/JWT chain path-matching allows).
        StepVerifier.create(controller.createProduct(currentUser, request))
                .expectErrorMatches(e -> e instanceof AccessDeniedException
                        || e instanceof AuthenticationCredentialsNotFoundException)
                .verify();
    }

    @Test
    void activateProduct_anonymousCaller_isDeniedBeforeReachingUseCase() {
        StepVerifier.create(controller.activateProduct(UUID.randomUUID()))
                .expectErrorMatches(e -> e instanceof AccessDeniedException
                        || e instanceof AuthenticationCredentialsNotFoundException)
                .verify();
    }
}
