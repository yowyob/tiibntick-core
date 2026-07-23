package com.yowyob.tiibntick.core.marketback.application.service;

import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.GenerateInvoiceUseCase;
import com.yowyob.tiibntick.core.billing.invoice.application.port.in.command.GenerateInvoiceCommand;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.InvoiceLine;
import com.yowyob.tiibntick.core.billing.invoice.domain.model.enums.LineItemType;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.CreditCommissionCommand;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.DebitWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.domain.enums.ClaimantType;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeCategory;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeCause;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputePriority;
import com.yowyob.tiibntick.core.dispute.domain.enums.RespondentType;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketOrderUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.PlaceMarketOrderCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.PlaceOrderFromQuoteCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.ProcessPaymentCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.MarketOrderResponse;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketNotificationPort;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketOrderRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IQuoteRequestRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IServiceOfferRepository;
import com.yowyob.tiibntick.core.marketback.domain.exception.InvalidClientException;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketOrderNotFoundException;
import com.yowyob.tiibntick.core.marketback.domain.exception.QuoteRequestNotFoundException;
import com.yowyob.tiibntick.core.marketback.domain.exception.ServiceOfferNotFoundException;
import com.yowyob.tiibntick.core.marketback.domain.model.Address;
import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryRequest;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrder;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketOrderId;
import com.yowyob.tiibntick.core.marketback.domain.model.Money;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderPricing;
import com.yowyob.tiibntick.core.marketback.domain.model.OrderStatus;
import com.yowyob.tiibntick.core.marketback.domain.model.ParcelSpec;
import com.yowyob.tiibntick.core.marketback.domain.model.PaymentInfo;
import com.yowyob.tiibntick.core.marketback.domain.model.QuoteRequest;
import com.yowyob.tiibntick.core.marketback.domain.model.QuoteRequestId;
import com.yowyob.tiibntick.core.marketback.domain.model.QuoteResponse;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceOfferId;
import com.yowyob.tiibntick.core.sales.application.port.in.CreateTntOrderLineCommand;
import com.yowyob.tiibntick.core.sales.application.port.in.CreateTntSalesOrderCommand;
import com.yowyob.tiibntick.core.sales.application.port.in.CreateTntSalesOrderUseCase;
import com.yowyob.tiibntick.core.sales.domain.model.OrderPriority;
import com.yowyob.tiibntick.core.sales.domain.model.TntAddress;
import com.yowyob.tiibntick.core.tp.application.port.in.command.EarnLoyaltyPointsCommand;
import com.yowyob.tiibntick.core.tp.application.service.LoyaltyService;
import com.yowyob.tiibntick.core.tp.application.service.TntClientProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Application service — manages the full MarketOrder lifecycle.
 * Covers: placement, payment, dispatch, tracking, completion and cancellation.
 *
 * <p>Mechanical port of the standalone tiibntick-market-backend's
 * {@code MarketOrderService}, adapted to this module's already-ported
 * {@code marketback} domain/port vocabulary (Money.amount() replacing the
 * old app's raw longs, MarketOrder's internal {@code pullDomainEvents()}
 * replacing manually-constructed event objects).</p>
 *
 * <p>Cross-module wiring (all same-JVM Spring bean injection, no HTTP):</p>
 * <ul>
 *   <li>tnt-actor-core — validates {@code clientId} against the Kernel on {@link #placeOrder}.</li>
 *   <li>tnt-tp-core — awards loyalty points and increments the delivery counter on {@link #completeOrder}
 *       (best-effort enrichment, via the concrete {@code @Service} classes since the tp-core inbound
 *       ports are package-private).</li>
 *   <li>tnt-delivery-core — best-effort status cross-check on {@link #dispatchOrder} (soft consistency
 *       check only; the Kafka-driven {@code MarketKafkaConsumer} remains the source of truth for
 *       status transitions).</li>
 *   <li>tnt-dispute-core — opens a {@code MARKET_CLAIM} dispute on {@link #cancelOrder} when the order
 *       had already progressed past placement (money/logistics commitment already made).</li>
 *   <li>tnt-realtime-core — TODO(market-migration): no natural touchpoint. {@code LiveETAUpdate} needs
 *       real GPS/ETA data that {@code MarketOrder} does not carry; fabricating placeholder coordinates
 *       would be worse than not wiring this integration at all. See {@link #markInTransit}.</li>
 *   <li>tnt-sales-core — records a formal {@code TntSalesOrder} on {@link #completeOrder} (best-effort).</li>
 *   <li>tnt-billing-invoice — generates an {@code Invoice} on {@link #confirmOrder} (best-effort).</li>
 *   <li>tnt-billing-wallet — real wallet debit/commission-credit on {@link #processPayment}
 *       (business-critical, see the reasoning documented on that method).</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketOrderApplicationService implements IManageMarketOrderUseCase {

    /** Default country code used when a market Address has none (module has no country field wired yet). */
    private static final String DEFAULT_COUNTRY_CODE = "CM";

    /** Flat loyalty-point award per completed market order (placeholder formula — no tiered rule yet). */
    private static final int LOYALTY_POINTS_PER_COMPLETED_ORDER = 10;

    private final IMarketOrderRepository orderRepository;
    private final IServiceOfferRepository offerRepository;
    private final IQuoteRequestRepository quoteRepository;
    private final IMarketEventPublisher eventPublisher;
    private final IMarketNotificationPort notificationPort;

    // ── Cross-module ports (wired 2026-07-11, see class javadoc) ──────────────
    private final IKernelActorPort actorPort;
    private final TntClientProfileService clientProfileService;
    private final LoyaltyService loyaltyService;
    private final DeliveryQueryUseCase deliveryQueryUseCase;
    private final IDisputeCommandUseCase disputeCommandUseCase;
    private final CreateTntSalesOrderUseCase salesOrderUseCase;
    private final GenerateInvoiceUseCase invoiceUseCase;
    private final IWalletUseCase walletUseCase;

    @Override
    @Transactional
    public Mono<MarketOrderResponse> placeOrder(PlaceMarketOrderCommand cmd) {
        log.debug("Placing MarketOrder for client={} offer={} tenant={}", cmd.clientId(), cmd.offerId(), cmd.tenantId());
        return actorPort.exists(cmd.clientId())
                .flatMap(exists -> {
                    if (!Boolean.TRUE.equals(exists)) {
                        return Mono.<MarketOrder>error(new InvalidClientException(cmd.clientId().toString()));
                    }
                    return offerRepository.findById(ServiceOfferId.of(cmd.offerId()))
                            .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(cmd.offerId().toString())))
                            .flatMap(offer -> {
                                ParcelSpec parcel = new ParcelSpec(cmd.parcelDescription(), cmd.weightKg(),
                                        cmd.lengthCm(), cmd.widthCm(), cmd.heightCm(), cmd.valueXaf(),
                                        cmd.fragile(), cmd.perishable(), cmd.requiresInsurance(), cmd.quantity());
                                Address pickup = new Address(cmd.pickupStreet(), cmd.pickupDistrict(), cmd.pickupCity(),
                                        null, null, cmd.pickupLat(), cmd.pickupLng(), null);
                                Address delivery = new Address(cmd.deliveryStreet(), cmd.deliveryDistrict(), cmd.deliveryCity(),
                                        null, null, cmd.deliveryLat(), cmd.deliveryLng(), null);
                                DeliveryRequest deliveryReq = new DeliveryRequest(pickup, delivery, parcel,
                                        null, null, cmd.urgency(), cmd.specialInstructions());
                                Money price = offer.estimatePrice(deliveryReq);
                                OrderPricing pricing = OrderPricing.fromEstimate(price);

                                MarketOrder order = MarketOrder.create(cmd.tenantId(), cmd.clientId(),
                                        offer.getProviderId(), offer.getListingId(), offer.getId(),
                                        deliveryReq, pricing);
                                // Domain events (MarketOrderCreatedEvent) are attached to `order` by the
                                // factory method — pull them before save() may hand back a freshly
                                // reconstituted instance with no pending events.
                                List<Object> events = order.pullDomainEvents();

                                return orderRepository.save(order)
                                        .flatMap(saved -> eventPublisher.publishAll(events, cmd.tenantId()).thenReturn(saved));
                            });
                })
                .map(this::toResponse);
    }

    /**
     * Places a MarketOrder from a QuoteRequest whose client already picked a response via
     * {@code IManageQuoteRequestUseCase#selectQuoteResponse}. Provider, listing, delivery request
     * and pricing are all derived from the QuoteRequest aggregate + its selected QuoteResponse —
     * see {@link MarketOrder#fromQuote}. Reuses {@link IQuoteRequestRepository}, the same outbound
     * port {@code QuoteRequestApplicationService} uses, rather than a new one.
     *
     * <p>Also calls {@link QuoteRequest#markConvertedToOrder()} and persists the quote — this is
     * the "different vertical" that {@link QuoteRequest}'s class javadoc refers to
     * ("later converted into a MarketOrder"), previously never wired up anywhere. Besides
     * completing the QuoteRequest state machine, this doubles as the guard against converting the
     * same quote into two orders: {@code markConvertedToOrder} itself throws once status is no
     * longer {@code SELECTED}, i.e. on a second call for the same quote.</p>
     */
    @Override
    @Transactional
    public Mono<MarketOrderResponse> placeOrderFromQuote(PlaceOrderFromQuoteCommand cmd) {
        log.debug("Placing MarketOrder from quote={} client={} tenant={}", cmd.quoteRequestId(), cmd.clientId(), cmd.tenantId());
        return quoteRepository.findById(QuoteRequestId.of(cmd.quoteRequestId()), cmd.tenantId())
                .switchIfEmpty(Mono.error(new QuoteRequestNotFoundException(cmd.quoteRequestId().toString())))
                .flatMap(quote -> {
                    if (!quote.hasSelectedResponse()) {
                        return Mono.error(new MarketDomainException(
                                "QuoteRequest " + cmd.quoteRequestId() + " has no selected response — call selectQuoteResponse first."));
                    }
                    QuoteResponse selectedResponse = quote.getSelectedResponse();
                    if (selectedResponse == null) {
                        return Mono.error(new MarketDomainException(
                                "QuoteRequest " + cmd.quoteRequestId() + " selected response could not be resolved."));
                    }

                    MarketOrder order = MarketOrder.fromQuote(quote, selectedResponse, cmd.tenantId());
                    List<Object> events = order.pullDomainEvents();

                    // Guards against double-conversion (throws MarketDomainException if the quote
                    // isn't SELECTED anymore, e.g. a retried/duplicate call).
                    quote.markConvertedToOrder();

                    return quoteRepository.save(quote)
                            .then(orderRepository.save(order))
                            .flatMap(saved -> eventPublisher.publishAll(events, cmd.tenantId()).thenReturn(saved));
                })
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<MarketOrderResponse> processPayment(UUID orderId, ProcessPaymentCommand cmd, String tenantId) {
        // ── tnt-billing-wallet semantics (business-critical — read before touching) ──────────────
        // ProcessPaymentCommand carries paymentMethod + transactionRef + paidAmountXaf: the shape of
        // a *payment confirmation callback* (client already paid MTN/Orange/Stripe/cash externally,
        // or authorized a wallet debit), not a "please charge this client now" initiation request —
        // tnt-billing-wallet's own InitiatePaymentCommand/PaymentIntent flow (IWalletUseCase#initiatePayment)
        // is what would own true initiation, and this module doesn't call it.
        //
        // Given that, we still perform REAL ledger movements here (not just an audit no-op):
        //   - debitWallet: only when paymentMethod == WALLET, i.e. the client actually paid from their
        //     TiiBnTick wallet balance. For MTN/Orange/Stripe/CASH_ON_DELIVERY the money never moved
        //     through the wallet, so calling debitWallet for those would be a meaningless/incorrect
        //     ledger entry (or a spurious insufficient-funds failure).
        //   - creditCommission: always, once payment is confirmed — this is real revenue owed to the
        //     provider and must be recorded regardless of payment channel.
        // Both calls happen BEFORE order.save(): if wallet-core fails, the Mono errors out and the
        // order is never persisted as PAID (nor are events published/notifications sent), so a retry
        // of processPayment is safe and the system never claims "paid" without the wallet ledger
        // agreeing. Errors are intentionally NOT swallowed (no .onErrorResume) — this is the actual
        // money-movement gap being closed.
        return findOrError(orderId, tenantId)
                .flatMap(order -> {
                    Money paidAmount = new Money(cmd.paidAmountXaf(), "XAF");
                    PaymentInfo payment = new PaymentInfo(cmd.paymentMethod(), cmd.transactionRef(),
                            LocalDateTime.now(), paidAmount, cmd.mobileMoneyPhone());
                    order.markAsPaid(payment);
                    List<Object> events = order.pullDomainEvents();
                    UUID tenantUuid = UUID.fromString(tenantId);

                    return processWalletMovement(tenantUuid, orderId, order, cmd, paidAmount)
                            .then(orderRepository.save(order))
                            .flatMap(saved -> eventPublisher.publishAll(events, tenantId)
                                    .then(notificationPort.notifyOrderPaid(tenantId, saved.getProviderId(),
                                            orderId.toString(), payment.paidAmount().amount()))
                                    .thenReturn(saved));
                })
                .map(this::toResponse);
    }

    /**
     * Performs the real wallet-core ledger movements for a confirmed payment. Not error-swallowing —
     * see the reasoning comment on {@link #processPayment}.
     */
    private Mono<Void> processWalletMovement(UUID tenantUuid, UUID orderId, MarketOrder order,
                                              ProcessPaymentCommand cmd, Money paidAmount) {
        com.yowyob.tiibntick.core.billing.wallet.domain.model.Money walletAmount =
                com.yowyob.tiibntick.core.billing.wallet.domain.model.Money.of(
                        BigDecimal.valueOf(paidAmount.amount()), paidAmount.currency());

        Mono<Void> debit = Mono.empty();
        if (cmd.paymentMethod() == com.yowyob.tiibntick.core.marketback.domain.model.PaymentMethod.WALLET) {
            DebitWalletCommand debitCmd = new DebitWalletCommand(
                    order.getClientId(), tenantUuid, walletAmount, orderId.toString(),
                    PaymentChannel.WALLET, "MarketOrder payment " + orderId, cmd.transactionRef());
            debit = walletUseCase.debitWallet(debitCmd).then();
        }

        String invoiceRef = order.getInvoiceId() != null ? order.getInvoiceId().toString() : orderId.toString();
        CreditCommissionCommand creditCmd = new CreditCommissionCommand(
                order.getProviderId(), tenantUuid, walletAmount, orderId.toString(), invoiceRef);
        Mono<Void> credit = walletUseCase.creditCommission(creditCmd).then();

        return debit.then(credit);
    }

    @Override
    public Mono<MarketOrderResponse> confirmOrder(UUID orderId, String tenantId) {
        return findOrError(orderId, tenantId)
                .flatMap(order -> { order.confirm(); return orderRepository.save(order); })
                .flatMap(saved -> generateInvoiceForOrder(tenantId, saved)
                        .flatMap(invoiceId -> {
                            saved.setInvoiceId(invoiceId);
                            return orderRepository.save(saved);
                        })
                        .defaultIfEmpty(saved))
                .map(this::toResponse);
    }

    /**
     * tnt-billing-invoice enrichment — generates an invoice for the just-confirmed order.
     * Non-critical: any failure logs a warning and completes empty so confirmOrder still succeeds.
     */
    private Mono<UUID> generateInvoiceForOrder(String tenantId, MarketOrder order) {
        OrderPricing pricing = order.getPricing();
        if (pricing == null) {
            return Mono.empty();
        }
        Money total = pricing.total();
        com.yowyob.tiibntick.core.billing.invoice.domain.model.Money unitPrice =
                com.yowyob.tiibntick.core.billing.invoice.domain.model.Money.of(
                        BigDecimal.valueOf(total.amount()), total.currency());
        InvoiceLine line = InvoiceLine.of(1, "TiiBnTick Market order " + order.getId().value(),
                1, unitPrice, BigDecimal.ZERO, LineItemType.DELIVERY_FEE);

        GenerateInvoiceCommand cmd = new GenerateInvoiceCommand(
                UUID.fromString(tenantId), tenantId, DEFAULT_COUNTRY_CODE,
                null, order.getId().value().toString(),
                order.getClientId().toString(), List.of(line), null,
                total.currency(), null,
                null, null, null, null, List.of(), null, null);

        return invoiceUseCase.generate(cmd)
                .map(invoice -> {
                    log.info("Generated invoice {} for confirmed MarketOrder {}", invoice.getId(), order.getId().value());
                    return invoice.getId();
                })
                .onErrorResume(e -> {
                    log.warn("tnt-billing-invoice: failed to generate invoice for MarketOrder {}: {}",
                            order.getId().value(), e.toString());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<MarketOrderResponse> dispatchOrder(UUID orderId, String deliveryMissionId, String tenantId) {
        return findOrError(orderId, tenantId)
                .flatMap(order -> {
                    order.dispatch(UUID.fromString(deliveryMissionId));
                    return orderRepository.save(order);
                })
                .flatMap(saved -> checkDeliveryConsistency(tenantId, deliveryMissionId).thenReturn(saved))
                .map(this::toResponse);
    }

    /**
     * tnt-delivery-core soft consistency check — best-effort only. tnt-market-back-core does not
     * create delivery missions directly (delivery-core creates them via its own announcement/bidding
     * flow); the Kafka-driven {@code MarketKafkaConsumer} is the real source of truth for status
     * transitions. This is purely a "does the mission still look sane" log-only check.
     */
    private Mono<Void> checkDeliveryConsistency(String tenantId, String deliveryMissionId) {
        UUID deliveryId;
        try {
            deliveryId = UUID.fromString(deliveryMissionId);
        } catch (IllegalArgumentException e) {
            log.warn("dispatchOrder: deliveryMissionId '{}' is not a valid UUID — skipping delivery-core consistency check",
                    deliveryMissionId);
            return Mono.empty();
        }
        return deliveryQueryUseCase.findDeliveryById(UUID.fromString(tenantId), deliveryId)
                .doOnNext(delivery -> {
                    DeliveryStatus status = delivery.getStatus();
                    if (status == DeliveryStatus.CANCELLED || status == DeliveryStatus.FAILED) {
                        log.warn("dispatchOrder: MarketOrder just dispatched but delivery-core Delivery {} is already {} — "
                                + "possible status inconsistency between market and delivery modules.", deliveryMissionId, status);
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                        "dispatchOrder: no delivery-core Delivery found for missionId={} — market/delivery-core may be out of sync.",
                        deliveryMissionId)))
                .onErrorResume(e -> {
                    log.warn("dispatchOrder: delivery-core consistency check failed for missionId={}: {}",
                            deliveryMissionId, e.toString());
                    return Mono.empty();
                })
                .then();
    }

    @Override
    public Mono<MarketOrderResponse> markInTransit(UUID orderId, String tenantId) {
        // TODO(market-migration): tnt-realtime-core's IBroadcastEtaUseCase#broadcastEtaUpdate needs
        // real GPS coordinates/ETA data that MarketOrder does not carry — no clean touchpoint here
        // without fabricating placeholder location data. Skipped deliberately (see class javadoc).
        return findOrError(orderId, tenantId)
                .flatMap(order -> { order.markInTransit(); return orderRepository.save(order); })
                .map(this::toResponse);
    }

    @Override
    public Mono<MarketOrderResponse> markDelivered(UUID orderId, String tenantId) {
        return findOrError(orderId, tenantId)
                .flatMap(order -> { order.markDelivered(); return orderRepository.save(order); })
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<MarketOrderResponse> completeOrder(UUID orderId, String tenantId) {
        return findOrError(orderId, tenantId)
                .flatMap(order -> {
                    order.complete();
                    List<Object> events = order.pullDomainEvents();
                    return orderRepository.save(order)
                            .flatMap(saved -> eventPublisher.publishAll(events, tenantId)
                                    .then(notificationPort.notifyOrderCompleted(tenantId, saved.getClientId(), orderId.toString()))
                                    .then(enrichOnCompletion(tenantId, saved))
                                    .thenReturn(saved));
                })
                .map(this::toResponse);
    }

    /**
     * Post-completion enrichments — tnt-tp-core (loyalty + delivery count) and tnt-sales-core
     * (formal sales order record). Both are non-critical: failures are logged and swallowed so a
     * hiccup in either module never fails order completion.
     */
    private Mono<Void> enrichOnCompletion(String tenantId, MarketOrder order) {
        return awardLoyaltyAndDeliveryCount(tenantId, order)
                .then(recordSalesOrder(tenantId, order))
                .onErrorResume(e -> {
                    log.warn("completeOrder: post-completion enrichment failed for order {}: {}",
                            order.getId().value(), e.toString());
                    return Mono.empty();
                });
    }

    private Mono<Void> awardLoyaltyAndDeliveryCount(String tenantId, MarketOrder order) {
        UUID tenantUuid = UUID.fromString(tenantId);
        UUID clientId = order.getClientId();
        String missionId = order.getId().value().toString();

        Mono<Void> incrementDeliveries = clientProfileService.incrementDeliveries(tenantUuid, clientId)
                .onErrorResume(e -> {
                    log.warn("tnt-tp-core: incrementDeliveries failed for client {} (order {}): {}",
                            clientId, missionId, e.toString());
                    return Mono.empty();
                })
                .then();

        EarnLoyaltyPointsCommand earnCmd = new EarnLoyaltyPointsCommand(
                tenantUuid, clientId, LOYALTY_POINTS_PER_COMPLETED_ORDER, missionId);
        Mono<Void> earnPoints = loyaltyService.earn(earnCmd)
                .onErrorResume(e -> {
                    log.warn("tnt-tp-core: loyalty earn failed for client {} (order {}): {}",
                            clientId, missionId, e.toString());
                    return Mono.empty();
                })
                .then();

        return incrementDeliveries.then(earnPoints);
    }

    /**
     * tnt-sales-core enrichment — records a formal sales order for the completed MarketOrder.
     * MarketOrder has no organizationId/agencyId concept of its own; providerId is used as a
     * best-effort stand-in for both, since the provider is the executing business entity.
     * listingId (or offerId as fallback) is used as the single order line's productId, since
     * MarketOrder does not model discrete product lines.
     */
    private Mono<Void> recordSalesOrder(String tenantId, MarketOrder order) {
        DeliveryRequest deliveryRequest = order.getDeliveryRequest();
        TntAddress deliveryAddress = toSalesAddress(
                deliveryRequest != null ? deliveryRequest.deliveryAddress() : null);
        if (deliveryAddress == null) {
            log.warn("tnt-sales-core: MarketOrder {} has no delivery address, skipping sales order recording",
                    order.getId().value());
            return Mono.empty();
        }

        OrderPricing pricing = order.getPricing();
        Money total = pricing != null ? pricing.total() : Money.zeroXaf();
        UUID productId = order.getListingId() != null ? order.getListingId().value()
                : order.getOfferId() != null ? order.getOfferId().value()
                : new UUID(0L, 0L);

        CreateTntOrderLineCommand line = new CreateTntOrderLineCommand(
                productId, "TiiBnTick Market order " + order.getId().value(), null,
                BigDecimal.ONE, BigDecimal.valueOf(total.amount()), total.currency(), null);

        UUID tenantUuid = UUID.fromString(tenantId);
        CreateTntSalesOrderCommand cmd = new CreateTntSalesOrderCommand(
                tenantUuid, order.getProviderId(), order.getProviderId(), order.getClientId(),
                List.of(line), deliveryAddress, null, OrderPriority.NORMAL, total.currency(),
                order.getClientId().toString(), null, order.getProviderId().toString());

        return salesOrderUseCase.createOrder(cmd)
                .doOnNext(so -> log.info("tnt-sales-core: recorded sales order {} for MarketOrder {}",
                        so.getId(), order.getId().value()))
                .onErrorResume(e -> {
                    log.warn("tnt-sales-core: failed to record sales order for MarketOrder {}: {}",
                            order.getId().value(), e.toString());
                    return Mono.empty();
                })
                .then();
    }

    private TntAddress toSalesAddress(Address address) {
        if (address == null) {
            return null;
        }
        String country = address.country() != null ? address.country() : DEFAULT_COUNTRY_CODE;
        return new TntAddress(address.street(), address.district(), address.city(), country,
                address.landmark(), address.lat(), address.lng(), null, null);
    }

    @Override
    public Mono<MarketOrderResponse> cancelOrder(UUID orderId, String reason, String tenantId) {
        return findOrError(orderId, tenantId)
                .flatMap(order -> {
                    OrderStatus previousStatus = order.getStatus();
                    order.cancel(reason);
                    return orderRepository.save(order)
                            .flatMap(saved -> openDisputeIfCommitted(tenantId, saved, previousStatus, reason)
                                    .thenReturn(saved));
                })
                .map(this::toResponse);
    }

    /**
     * tnt-dispute-core enrichment — opens a MARKET_CLAIM dispute when the order being cancelled had
     * already progressed past mere placement (CONFIRMED/PAID/DISPATCHED — money and/or logistics
     * commitment already made). Nothing is committed yet at DRAFT, so a plain cancellation is enough
     * there. IN_TRANSIT/DELIVERED/COMPLETED are included for documentation but are unreachable here:
     * {@code MarketOrder#cancel} itself already forbids cancelling from those states.
     * Non-critical: failures are logged and swallowed so cancellation always succeeds.
     */
    private Mono<Void> openDisputeIfCommitted(String tenantId, MarketOrder order,
                                               OrderStatus previousStatus, String reason) {
        boolean alreadyCommitted = previousStatus == OrderStatus.CONFIRMED
                || previousStatus == OrderStatus.PAID
                || previousStatus == OrderStatus.DISPATCHED
                || previousStatus == OrderStatus.IN_TRANSIT;
        if (!alreadyCommitted) {
            return Mono.empty();
        }

        OpenDisputeCommand cmd = new OpenDisputeCommand(
                tenantId, order.getClientId().toString(), ClaimantType.CLIENT,
                order.getProviderId().toString(), RespondentType.PLATFORM,
                DisputeCause.NON_DELIVERY, DisputeCategory.MARKET_CLAIM, DisputePriority.NORMAL,
                order.getId().value().toString(), null, null, reason,
                null, null, false);

        return disputeCommandUseCase.openDispute(cmd)
                .doOnNext(dispute -> log.info("tnt-dispute-core: opened MARKET_CLAIM dispute {} for cancelled order {} (was {})",
                        dispute.getId(), order.getId().value(), previousStatus))
                .onErrorResume(e -> {
                    log.warn("tnt-dispute-core: failed to open dispute for cancelled order {}: {}",
                            order.getId().value(), e.toString());
                    return Mono.empty();
                })
                .then();
    }

    @Override
    public Mono<MarketOrderResponse> getOrder(UUID orderId, String tenantId) {
        return findOrError(orderId, tenantId).map(this::toResponse);
    }

    @Override
    public Flux<MarketOrderResponse> getOrdersByClient(UUID clientId, String tenantId) {
        return orderRepository.findByClientId(clientId, tenantId).map(this::toResponse);
    }

    @Override
    public Flux<MarketOrderResponse> getOrdersByProvider(UUID providerId, String tenantId) {
        return orderRepository.findByProviderId(providerId, tenantId).map(this::toResponse);
    }

    @Override
    public Flux<MarketOrderResponse> getOrdersByStatus(OrderStatus status, String tenantId) {
        return orderRepository.findByStatus(status, tenantId).map(this::toResponse);
    }

    private Mono<MarketOrder> findOrError(UUID orderId, String tenantId) {
        return orderRepository.findById(MarketOrderId.of(orderId), tenantId)
                .switchIfEmpty(Mono.error(new MarketOrderNotFoundException(orderId.toString())));
    }

    private MarketOrderResponse toResponse(MarketOrder o) {
        OrderPricing pricing = o.getPricing();
        PaymentInfo payment = o.getPaymentInfo();
        DeliveryRequest deliveryRequest = o.getDeliveryRequest();
        return new MarketOrderResponse(
                o.getId().value(), o.getTenantId(), o.getClientId(), o.getProviderId(),
                o.getListingId() != null ? o.getListingId().value() : null,
                o.getOfferId() != null ? o.getOfferId().value() : null,
                o.getQuoteRequestId() != null ? o.getQuoteRequestId().value() : null,
                o.getStatus(),
                deliveryRequest != null ? deliveryRequest.pickupAddress().city() : null,
                deliveryRequest != null ? deliveryRequest.deliveryAddress().city() : null,
                deliveryRequest != null ? deliveryRequest.parcelSpec().weightKg() : 0,
                pricing != null ? pricing.total().amount() : 0,
                pricing != null && pricing.discount() != null ? pricing.discount().amount() : 0,
                payment != null ? payment.paymentMethod() : null,
                payment != null ? payment.transactionRef() : null,
                o.getMissionId(), o.getInvoiceId(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
