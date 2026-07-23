package com.yowyob.tiibntick.core.sales.application.service;

import com.yowyob.tiibntick.core.sales.application.port.in.*;
import com.yowyob.tiibntick.core.sales.application.port.out.*;
import com.yowyob.tiibntick.core.sales.domain.event.*;
import com.yowyob.tiibntick.core.sales.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Central application service for tnt-sales-core.
 *
 * <p>Orchestrates the complete TiiBnTick SalesOrder lifecycle and event publishing.
 * All operations are reactive (non-blocking, Reactor Mono/Flux).</p>
 *
 * <p><b>Kernel integration:</b> When creating a new order, this service optionally queries
 * the Kernel (RT-comops-sales-core) via {@link KernelSalesOrderPort} to resolve a matching
 * {@code kernelSalesOrderId}. The Kernel link is best-effort and non-blocking — the TNT
 * order is always created even when no Kernel counterpart exists (informal transactions).</p>
 *
 * @author MANFOUO Braun
 */
@Service
public class SalesApplicationService implements
        CreateTntSalesOrderUseCase,
        ConfirmSalesOrderUseCase,
        ReserveStockUseCase,
        DispatchSalesOrderUseCase,
        StartDeliveryUseCase,
        MarkDeliveredUseCase,
        ReturnSalesOrderUseCase,
        CancelSalesOrderUseCase,
        LinkInvoiceToOrderUseCase,
        GetSalesOrderUseCase,
        ListSalesOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(SalesApplicationService.class);

    private final TntSalesOrderRepository orderRepository;
    private final SalesEventPublisher eventPublisher;
    private final OrderNumberGeneratorPort numberGenerator;

    /**
     * Outbound port to the Yowyob Kernel sales domain.
     * Optional — used only during order creation for best-effort Kernel link resolution.
     */
    private final KernelSalesOrderPort kernelSalesOrderPort;

    public SalesApplicationService(TntSalesOrderRepository orderRepository,
                                    SalesEventPublisher eventPublisher,
                                    OrderNumberGeneratorPort numberGenerator,
                                    KernelSalesOrderPort kernelSalesOrderPort) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.numberGenerator = numberGenerator;
        this.kernelSalesOrderPort = kernelSalesOrderPort;
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Generates a unique order number, optionally resolves a Kernel sales order ID
     * (best-effort — failure returns an unlinked order), builds the aggregate, and persists it.</p>
     */
    @Override
    public Mono<TntSalesOrder> createOrder(CreateTntSalesOrderCommand cmd) {
        int year = LocalDate.now().getYear();
        return numberGenerator.generate(cmd.tenantId(), cmd.agencyId(), year)
                .flatMap(orderNumber -> {
                    List<TntSalesOrderLine> lines = cmd.lines().stream()
                            .map(l -> TntSalesOrderLine.withNotes(l.productId(), l.productName(),
                                    l.sku(), l.quantity(), l.unitPrice(), l.currency(), l.notes()))
                            .toList();
                    // Attempt to resolve a Kernel sales order link (optional, non-blocking)
                    return kernelSalesOrderPort
                            .findByClientAndOrganization(cmd.tenantId(),
                                    cmd.clientThirdPartyId(), cmd.organizationId())
                            .map(kernelDto -> {
                                log.debug("Linked TNT order to Kernel salesOrderId={}",
                                        kernelDto.kernelSalesOrderId());
                                TntSalesOrder order = TntSalesOrder.create(cmd.tenantId(), cmd.organizationId(),
                                        cmd.agencyId(), cmd.clientThirdPartyId(), orderNumber, lines,
                                        cmd.deliveryAddress(), cmd.billingAddress(),
                                        cmd.priority(), cmd.currency(),
                                        kernelDto.kernelSalesOrderId());
                            // : attach FreelancerOrg provider context if present
                            if (cmd.providerOrgType() != null && cmd.providerOrgId() != null) {
                                order = order.withProviderOrg(cmd.providerOrgType(), cmd.providerOrgId());
                            }
                            return order;
                            })
                            .defaultIfEmpty(
                                // Kernel link is optional — proceed without it
                                TntSalesOrder.create(cmd.tenantId(), cmd.organizationId(),
                                        cmd.agencyId(), cmd.clientThirdPartyId(), orderNumber, lines,
                                        cmd.deliveryAddress(), cmd.billingAddress(),
                                        cmd.priority(), cmd.currency())
                            )
                            .flatMap(orderRepository::save);
                });
    }

    // ─── Confirm ──────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public Mono<TntSalesOrder> confirmOrder(UUID tenantId, UUID orderId) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)))
                .map(TntSalesOrder::confirm)
                .flatMap(orderRepository::save)
                .flatMap(saved -> eventPublisher.publishOrderConfirmed(SalesOrderConfirmedEvent.of(saved))
                        .thenReturn(saved));
    }

    // ─── Reserve Stock ────────────────────────────────────────────────────────

    @Override
    public Mono<TntSalesOrder> reserveStock(UUID tenantId, UUID orderId) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)))
                .map(TntSalesOrder::reserveStock)
                .flatMap(orderRepository::save);
    }

    // ─── Dispatch ─────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public Mono<TntSalesOrder> dispatch(UUID tenantId, UUID orderId, UUID missionId) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)))
                .map(order -> order.dispatch(missionId))
                .flatMap(orderRepository::save)
                .flatMap(saved -> eventPublisher.publishOrderDispatched(SalesOrderDispatchedEvent.of(saved))
                        .thenReturn(saved));
    }

    // ─── Start Delivery ───────────────────────────────────────────────────────

    @Override
    public Mono<TntSalesOrder> startDelivery(UUID tenantId, UUID orderId) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)))
                .map(TntSalesOrder::startDelivery)
                .flatMap(orderRepository::save);
    }

    // ─── Mark Delivered ───────────────────────────────────────────────────────

    @Transactional
    @Override
    public Mono<TntSalesOrder> markDelivered(UUID tenantId, UUID orderId) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)))
                .map(TntSalesOrder::markDelivered)
                .flatMap(orderRepository::save)
                .flatMap(saved -> eventPublisher.publishOrderDelivered(SalesOrderDeliveredEvent.of(saved))
                        .thenReturn(saved));
    }

    // ─── Return ───────────────────────────────────────────────────────────────

    @Override
    public Mono<TntSalesOrder> returnOrder(UUID tenantId, UUID orderId,
                                             ReturnReason reason, String note) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)))
                .map(order -> order.markReturned(reason, note))
                .flatMap(orderRepository::save);
    }

    // ─── Cancel ───────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public Mono<TntSalesOrder> cancelOrder(UUID tenantId, UUID orderId, String reason) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)))
                .map(order -> order.cancel(reason))
                .flatMap(orderRepository::save)
                .flatMap(saved -> eventPublisher.publishOrderCancelled(SalesOrderCancelledEvent.of(saved))
                        .thenReturn(saved));
    }

    // ─── Link Invoice ─────────────────────────────────────────────────────────

    @Override
    public Mono<TntSalesOrder> linkInvoice(UUID tenantId, UUID orderId, UUID invoiceId) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)))
                .map(order -> order.linkInvoice(invoiceId))
                .flatMap(orderRepository::save);
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    @Override
    public Mono<TntSalesOrder> getOrder(UUID tenantId, UUID orderId) {
        return orderRepository.findById(tenantId, orderId)
                .switchIfEmpty(Mono.error(orderNotFound(orderId)));
    }

    @Override
    public Flux<TntSalesOrder> listByClient(UUID tenantId, UUID clientThirdPartyId) {
        return orderRepository.findByClientThirdPartyId(tenantId, clientThirdPartyId);
    }

    @Override
    public Flux<TntSalesOrder> listByAgency(UUID tenantId, UUID agencyId, YearMonth period) {
        return orderRepository.findByAgencyAndPeriod(tenantId, agencyId,
                period.getYear(), period.getMonthValue());
    }

    @Override
    public Flux<TntSalesOrder> listByStatus(UUID tenantId, SalesOrderStatus status) {
        return orderRepository.findByStatus(tenantId, status);
    }

    @Override
    public Flux<TntSalesOrder> listPendingDispatch(UUID tenantId) {
        return orderRepository.findByStatus(tenantId, SalesOrderStatus.STOCK_RESERVED);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ResponseStatusException orderNotFound(UUID orderId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "SalesOrder not found: " + orderId);
    }
}
