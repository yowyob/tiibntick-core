package com.yowyob.tiibntick.core.sales.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.sales.adapter.out.persistence.entity.TntSalesOrderLineEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for TntSalesOrderLine entities.
 * Author: MANFOUO Braun
 */
public interface TntSalesOrderLineR2dbcRepository extends ReactiveCrudRepository<TntSalesOrderLineEntity, UUID> {

    @Query("SELECT * FROM sales.order_lines WHERE order_id = :orderId")
    Flux<TntSalesOrderLineEntity> findByOrderId(UUID orderId);

    @Query("DELETE FROM sales.order_lines WHERE order_id = :orderId")
    Mono<Void> deleteByOrderId(UUID orderId);
}
