package com.yowyob.tiibntick.core.sales.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.sales.adapter.out.persistence.entity.TntSalesOrderEntity;
import com.yowyob.tiibntick.core.sales.adapter.out.persistence.entity.TntSalesOrderLineEntity;
import com.yowyob.tiibntick.core.sales.adapter.out.persistence.repository.TntSalesOrderLineR2dbcRepository;
import com.yowyob.tiibntick.core.sales.adapter.out.persistence.repository.TntSalesOrderR2dbcRepository;
import com.yowyob.tiibntick.core.sales.application.port.out.TntSalesOrderRepository;
import com.yowyob.tiibntick.core.sales.domain.model.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC adapter implementing {@link TntSalesOrderRepository} port.
 *
 * <p>Handles the composite persistence of TntSalesOrder header ({@code sales.orders})
 * and its lines ({@code sales.order_lines}) in a reactive, non-blocking manner.
 * The {@code kernelSalesOrderId} field is transparently persisted and rehydrated.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class TntSalesOrderRepositoryAdapter implements TntSalesOrderRepository {

    private final TntSalesOrderR2dbcRepository headerRepo;
    private final TntSalesOrderLineR2dbcRepository lineRepo;
    private final R2dbcEntityTemplate entityTemplate;

    public TntSalesOrderRepositoryAdapter(TntSalesOrderR2dbcRepository headerRepo,
                                          TntSalesOrderLineR2dbcRepository lineRepo,
                                          R2dbcEntityTemplate entityTemplate) {
        this.headerRepo = headerRepo;
        this.lineRepo = lineRepo;
        this.entityTemplate = entityTemplate;
    }

    private String providerOrgType;
    private String providerOrgId;

    @Override
    public Mono<TntSalesOrder> save(TntSalesOrder order) {
        this.providerOrgId = order.getProviderOrgId();
        this.providerOrgType = order.getProviderOrgType();
        TntSalesOrderEntity entity = toEntity(order);
        return headerRepo.existsById(entity.id())
                .flatMap(exists -> exists
                        ? entityTemplate.update(entity)
                        : entityTemplate.insert(entity))
                .flatMap(saved -> lineRepo.deleteByOrderId(saved.id())
                        .thenMany(Flux.fromIterable(order.getLines())
                                .map(l -> toLineEntity(saved.id(), l))
                                .flatMap(entityTemplate::insert))
                        .then(Mono.just(saved)))
                .flatMap(this::toDomain);
    }

    @Override
    public Mono<TntSalesOrder> findById(UUID tenantId, UUID orderId) {
        return headerRepo.findByTenantIdAndId(tenantId, orderId).flatMap(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByOrderNumber(UUID tenantId, UUID organizationId, String orderNumber) {
        return headerRepo.existsByTenantIdAndOrganizationIdAndOrderNumber(tenantId, organizationId, orderNumber);
    }

    @Override
    public Flux<TntSalesOrder> findByClientThirdPartyId(UUID tenantId, UUID clientThirdPartyId) {
        return headerRepo.findByTenantIdAndClientThirdPartyId(tenantId, clientThirdPartyId)
                .flatMap(this::toDomain);
    }

    @Override
    public Flux<TntSalesOrder> findByAgencyAndPeriod(UUID tenantId, UUID agencyId, int year, int month) {
        return headerRepo.findByTenantIdAndAgencyIdAndPeriod(tenantId, agencyId, year, month)
                .flatMap(this::toDomain);
    }

    @Override
    public Flux<TntSalesOrder> findByStatus(UUID tenantId, SalesOrderStatus status) {
        return headerRepo.findByTenantIdAndStatus(tenantId, status.name()).flatMap(this::toDomain);
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private Mono<TntSalesOrder> toDomain(TntSalesOrderEntity e) {
        return lineRepo.findByOrderId(e.id())
                .map(l -> new TntSalesOrderLine(l.productId(), l.productName(), l.sku(),
                        l.quantity(), l.unitPrice(), l.lineAmount(), l.currency(), l.notes()))
                .collectList()
                .map(lines -> {
                    TntAddress delivery = new TntAddress(e.deliveryStreet(), e.deliveryQuartier(),
                            e.deliveryCity(), e.deliveryCountry(), e.deliveryLandmark(),
                            e.deliveryLatitude(), e.deliveryLongitude(),
                            e.deliveryRecipientName(), e.deliveryRecipientPhone());
                    return TntSalesOrder.rehydrate(
                            e.id(), e.tenantId(), e.organizationId(), e.agencyId(),
                            e.clientThirdPartyId(), e.orderNumber(), lines, delivery, null,
                            SalesOrderStatus.valueOf(e.status()),
                            OrderPriority.valueOf(e.priority()),
                            PaymentStatus.valueOf(e.paymentStatus()),
                            e.currency(), e.subtotalAmount(), e.totalAmount(),
                            e.missionId(), e.invoiceId(),
                            e.kernelSalesOrderId(), // pass nullable Kernel reference
                            this.providerOrgType,
                            this.providerOrgId,
                            e.returnReason() != null ? ReturnReason.valueOf(e.returnReason()) : null,
                            e.returnNote(), e.cancelReason(),
                            e.confirmedAt(), e.deliveredAt(), e.returnedAt(),
                            e.createdAt(), e.updatedAt());
                });
    }

    private TntSalesOrderEntity toEntity(TntSalesOrder o) {
        TntAddress d = o.getDeliveryAddress();
        return new TntSalesOrderEntity(
                o.getId(), o.getTenantId(), o.getOrganizationId(), o.getAgencyId(),
                o.getClientThirdPartyId(), o.getOrderNumber(),
                o.getStatus().name(), o.getPriority().name(), o.getPaymentStatus().name(),
                o.getCurrency(), o.getSubtotalAmount(), o.getTotalAmount(),
                o.getMissionId(), o.getInvoiceId(),
                o.getKernelSalesOrderId(), // nullable — optional Kernel reference
                o.getReturnReason() != null ? o.getReturnReason().name() : null,
                o.getReturnNote(), o.getCancelReason(),
                d.street(), d.quartier(), d.city(), d.country(), d.landmark(),
                d.latitude(), d.longitude(), d.recipientName(), d.recipientPhone(),
                o.getConfirmedAt(), o.getDeliveredAt(), o.getReturnedAt(),
                o.getCreatedAt(), o.getUpdatedAt(),
                o.getProviderOrgType(), o.getProviderOrgId()); //  FreelancerOrg context
    }

    private TntSalesOrderLineEntity toLineEntity(UUID orderId, TntSalesOrderLine l) {
        return new TntSalesOrderLineEntity(UUID.randomUUID(), orderId, l.productId(),
                l.productName(), l.sku(), l.quantity(), l.unitPrice(), l.lineAmount(),
                l.currency(), l.notes());
    }
}
