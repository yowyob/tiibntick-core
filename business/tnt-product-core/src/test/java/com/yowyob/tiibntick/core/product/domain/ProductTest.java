package com.yowyob.tiibntick.core.product.domain;

import com.yowyob.tiibntick.common.vo.Money;
import com.yowyob.tiibntick.core.product.domain.exception.ProductStatusTransitionException;
import com.yowyob.tiibntick.core.product.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Product aggregate — including catalogProductId Kernel integration key.
 *
 * @author MANFOUO Braun
 */
class ProductTest {

    private Product buildProduct() {
        return Product.create(UUID.randomUUID(), null,
                "SKU001", "Colis Standard", "Standard parcel",
                null, ProductType.PHYSICAL_GOOD,
                Money.of(BigDecimal.valueOf(5000), "XAF"),
                UnitOfMeasure.PARCEL, 5.0, null, LogisticsProfile.standard(),
                List.of(), Map.of());
    }

    @Test
    void should_create_product_in_draft_status() {
        Product product = buildProduct();
        assertThat(product.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.id()).isNotNull();
        assertThat(product.catalogProductId()).isNull();
    }

    @Test
    void should_set_catalog_product_id_at_creation() {
        UUID catalogId = UUID.randomUUID();
        Product product = Product.create(UUID.randomUUID(), catalogId,
                "SKU002", "Kernel Linked Product", null,
                null, ProductType.PHYSICAL_GOOD,
                Money.of(BigDecimal.valueOf(10000), "XAF"),
                UnitOfMeasure.UNIT, null, null, LogisticsProfile.standard(),
                List.of(), Map.of());

        assertThat(product.catalogProductId()).isEqualTo(catalogId);
    }

    @Test
    void should_link_kernel_id_post_creation_via_withCatalogProductId() {
        Product product = buildProduct();
        UUID kernelId = UUID.randomUUID();

        Product linked = product.withCatalogProductId(kernelId);

        assertThat(linked.catalogProductId()).isEqualTo(kernelId);
        assertThat(product.catalogProductId()).isNull(); // original unchanged
        assertThat(linked.sku()).isEqualTo(product.sku());
    }

    @Test
    void should_activate_from_draft() {
        Product product = buildProduct().activate();
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void should_not_activate_archived_product() {
        Product archived = buildProduct().archive();
        assertThatThrownBy(archived::activate)
                .isInstanceOf(ProductStatusTransitionException.class);
    }

    @Test
    void should_use_tnt_common_core_money() {
        // tnt-common-core Money uses getAmount() / getCurrencyCode() (not record accessors)
        Money price = Money.of(BigDecimal.valueOf(25000), "XAF");
        assertThat(price.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(25000));
        assertThat(price.getCurrencyCode()).isEqualTo("XAF");
        assertThat(price.isPositive()).isTrue();

        // Arithmetic operations (richer than old local Money)
        Money doubled = price.multiply(2.0);
        assertThat(doubled.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    void should_rehydrate_with_catalog_product_id() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();

        Product product = Product.rehydrate(id, tenantId, catalogId,
                "SKU003", "Rehydrated", null, null,
                ProductType.PHYSICAL_GOOD,
                Money.of(BigDecimal.ONE, "XAF"),
                UnitOfMeasure.UNIT, null, null, LogisticsProfile.standard(),
                ProductStatus.ACTIVE, List.of(), List.of(), List.of(), Map.of(),
                now, now);

        assertThat(product.id()).isEqualTo(id);
        assertThat(product.catalogProductId()).isEqualTo(catalogId);
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    }
}
