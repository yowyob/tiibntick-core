package com.yowyob.tiibntick.core.sales.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * R2DBC entity for TntSalesOrderLine (sales.order_lines table).
 * Author: MANFOUO Braun
 */
@Table(schema = "sales", name = "order_lines")
public record TntSalesOrderLineEntity(
        @Id @Column("id") UUID id,
        @Column("order_id") UUID orderId,
        @Column("product_id") UUID productId,
        @Column("product_name") String productName,
        @Column("sku") String sku,
        @Column("quantity") BigDecimal quantity,
        @Column("unit_price") BigDecimal unitPrice,
        @Column("line_amount") BigDecimal lineAmount,
        @Column("currency") String currency,
        @Column("notes") String notes
) {}
