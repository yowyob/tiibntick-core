package com.yowyob.tiibntick.core.marketback.adapter.in.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketCampaignUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketListingUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMarketOrderUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageMerchantContractUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageProviderReviewUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageQuoteRequestUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageServiceOfferUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateCampaignCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateMarketListingCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateQuoteRequestCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateServiceOfferCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.InitContractNegotiationCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.PlaceMarketOrderCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.PlaceOrderFromQuoteCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.ProcessPaymentCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.RejectQuoteResponseCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.RenewContractCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.SubmitQuoteResponseCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.SubmitReviewCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.UpdateMarketListingCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.UpdateServiceOfferCommand;
import com.yowyob.tiibntick.core.marketback.domain.model.PaymentMethod;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationApplier;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter — implements {@code tnt-sync-core}'s {@link IOfflineOperationApplier} SPI so that
 * offline-sync pushes for Market aggregates ({@code OfflineOpType.MARKET_COMMAND}) actually
 * execute the real Market use-case instead of only journaling {@code entity_version}.
 *
 * <h2>Envelope shape</h2>
 * Every {@code MARKET_COMMAND} offline operation's {@code payload} must be this JSON envelope,
 * where {@code op.aggregateType} (a field of the offline operation itself, set by the client —
 * NOT read from inside this envelope) must be one of the {@code MARKET_*} constants declared
 * below, and {@code commandName} selects the dispatch case in this class:
 * <pre>{@code
 * {
 *   "commandName": "PLACE_ORDER",
 *   "aggregateType": "MARKET_ORDER",   // informational only, for readability; not read by this class
 *   "command": { ... }                 // shape depends on commandName, see table below
 * }
 * }</pre>
 *
 * <h2>{@link #MARKET_LISTING} — {@link IManageMarketListingUseCase}</h2>
 * <ul>
 *   <li>{@code CREATE_LISTING} → {@code createListing}. {@code command} = full {@link CreateMarketListingCommand} JSON.</li>
 *   <li>{@code UPDATE_LISTING} → {@code updateListing}. {@code command} = {@code {listingId, tenantId,
 *       displayName, tagline, description, contactEmail, contactPhone, websiteUrl, socialLinks,
 *       certificationIds, foundedYear, cities, radiusKm, centerLat, centerLng}}.</li>
 *   <li>{@code SUBMIT_LISTING_FOR_REVIEW} → {@code submitForReview}. {@code command} = {@code {id, tenantId}} (id = listingId).</li>
 *   <li>{@code APPROVE_LISTING} → {@code approveListing}. {@code command} = {@code {id, actorId, tenantId}} (id = listingId, actorId = adminId).</li>
 *   <li>{@code REJECT_LISTING} → {@code rejectListing}. {@code command} = {@code {id, actorId, reason, tenantId}} (id = listingId, actorId = adminId).</li>
 *   <li>{@code UNPUBLISH_LISTING} → {@code unpublishListing}. {@code command} = {@code {id, tenantId}} (id = listingId).</li>
 *   <li>{@code SUSPEND_LISTING} → {@code suspendListing}. {@code command} = {@code {id, reason, tenantId}} (id = listingId).</li>
 *   <li>{@code TRACK_VIEW} → {@code trackView} (Mono&lt;Void&gt; → returns {@code "{}"}). {@code command} = {@code {listingId, tenantId}}.</li>
 *   <li>{@code DELETE_LISTING} → {@code deleteListing} (Mono&lt;Void&gt; → returns {@code "{}"}). {@code command} = {@code {id, tenantId}} (id = listingId).</li>
 * </ul>
 *
 * <h2>{@link #MARKET_SERVICE_OFFER} — {@link IManageServiceOfferUseCase}</h2>
 * <ul>
 *   <li>{@code CREATE_SERVICE_OFFER} → {@code createOffer}. {@code command} = full {@link CreateServiceOfferCommand} JSON.</li>
 *   <li>{@code UPDATE_SERVICE_OFFER} → {@code updateOffer}. {@code command} = {@code {offerId, tenantId,
 *       name, description, serviceType, basePriceXaf, perKmRateXaf, perKgRateXaf, minimumPriceXaf,
 *       pricingDslExpression, maxWeightKg, acceptsFragile, acceptsPerishable, daysOfWeek,
 *       expressAvailable, sameDayAvailable}}.</li>
 *   <li>{@code ACTIVATE_SERVICE_OFFER} → {@code activateOffer}. {@code command} = {@code {id, tenantId}} (id = offerId).</li>
 *   <li>{@code DEACTIVATE_SERVICE_OFFER} → {@code deactivateOffer}. {@code command} = {@code {id, tenantId}} (id = offerId).</li>
 *   <li>{@code ARCHIVE_SERVICE_OFFER} → {@code archiveOffer}. {@code command} = {@code {id, tenantId}} (id = offerId).</li>
 * </ul>
 *
 * <h2>{@link #MARKET_QUOTE_REQUEST} — {@link IManageQuoteRequestUseCase}</h2>
 * <ul>
 *   <li>{@code CREATE_QUOTE_REQUEST} → {@code createQuoteRequest}. {@code command} = full {@link CreateQuoteRequestCommand} JSON.</li>
 *   <li>{@code SUBMIT_QUOTE_RESPONSE} → {@code submitQuoteResponse}. {@code command} = {@code {quoteRequestId, tenantId,
 *       providerId, proposedPriceXaf, estimatedPickupAt, estimatedDeliveryAt, etaHours, message, conditions, validHours}}.</li>
 *   <li>{@code SELECT_QUOTE_RESPONSE} → {@code selectQuoteResponse}. {@code command} = {@code {quoteRequestId, responseId, clientId, tenantId}}.</li>
 *   <li>{@code REJECT_QUOTE_RESPONSE} → {@code rejectQuoteResponse}. {@code command} = full
 *       {@link RejectQuoteResponseCommand} JSON: {@code {tenantId, quoteRequestId, responseId, clientId}}.</li>
 *   <li>{@code CANCEL_QUOTE_REQUEST} → {@code cancelQuoteRequest}. {@code command} = {@code {quoteRequestId, reason, clientId, tenantId}}.</li>
 * </ul>
 *
 * <h2>{@link #MARKET_ORDER} — {@link IManageMarketOrderUseCase}</h2>
 * <ul>
 *   <li>{@code PLACE_ORDER} → {@code placeOrder}. {@code command} = full {@link PlaceMarketOrderCommand} JSON.</li>
 *   <li>{@code PLACE_ORDER_FROM_QUOTE} → {@code placeOrderFromQuote}. {@code command} = full
 *       {@link PlaceOrderFromQuoteCommand} JSON: {@code {tenantId, quoteRequestId, clientId}}. The
 *       target {@link com.yowyob.tiibntick.core.marketback.domain.model.QuoteRequest} must already
 *       have a selected response (i.e. {@code SELECT_QUOTE_RESPONSE} was applied first).</li>
 *   <li>{@code CONFIRM_ORDER} → {@code confirmOrder}. {@code command} = {@code {id, tenantId}} (id = orderId).</li>
 *   <li>{@code CONFIRM_ORDER_PAYMENT} → {@code processPayment}. {@code command} = {@code {orderId, tenantId,
 *       paymentMethod, transactionRef, paidAmountXaf, mobileMoneyPhone}}.</li>
 *   <li>{@code DISPATCH_ORDER} → {@code dispatchOrder}. {@code command} = {@code {orderId, deliveryMissionId, tenantId}}.</li>
 *   <li>{@code MARK_ORDER_IN_TRANSIT} → {@code markInTransit}. {@code command} = {@code {id, tenantId}} (id = orderId).</li>
 *   <li>{@code DELIVER_ORDER} → {@code markDelivered}. {@code command} = {@code {id, tenantId}} (id = orderId).</li>
 *   <li>{@code COMPLETE_ORDER} → {@code completeOrder}. {@code command} = {@code {id, tenantId}} (id = orderId).</li>
 *   <li>{@code CANCEL_ORDER} → {@code cancelOrder}. {@code command} = {@code {id, reason, tenantId}} (id = orderId).</li>
 * </ul>
 *
 * <h2>{@link #MARKET_PROVIDER_REVIEW} — {@link IManageProviderReviewUseCase}</h2>
 * <ul>
 *   <li>{@code CREATE_REVIEW} → {@code submitReview}. {@code command} = full {@link SubmitReviewCommand} JSON.</li>
 *   <li>{@code APPROVE_REVIEW} → {@code approveReview}. {@code command} = {@code {id, actorId, tenantId}} (id = reviewId, actorId = adminId).</li>
 *   <li>{@code REJECT_REVIEW} → {@code rejectReview}. {@code command} = {@code {id, actorId, reason, tenantId}} (id = reviewId, actorId = adminId).</li>
 *   <li>{@code FLAG_REVIEW} → {@code flagReview}. {@code command} = {@code {id, reason, tenantId}} (id = reviewId).</li>
 * </ul>
 *
 * <h2>{@link #MARKET_MERCHANT_CONTRACT} — {@link IManageMerchantContractUseCase}</h2>
 * <ul>
 *   <li>{@code CREATE_CONTRACT_NEGOTIATION} → {@code initNegotiation}. {@code command} = full {@link InitContractNegotiationCommand} JSON.</li>
 *   <li>{@code SIGN_CONTRACT_BY_MERCHANT} → {@code signByMerchant}. {@code command} = {@code {id, actorId, tenantId}} (id = contractId, actorId = merchantId).</li>
 *   <li>{@code COUNTERSIGN_CONTRACT_BY_PROVIDER} → {@code countersignByProvider}. {@code command} = {@code {id, actorId, tenantId}} (id = contractId, actorId = providerId).</li>
 *   <li>{@code TERMINATE_CONTRACT} → {@code terminateContract}. {@code command} = {@code {id, reason, tenantId}} (id = contractId).</li>
 *   <li>{@code RENEW_CONTRACT} → {@code renewContract}. {@code command} = {@code {contractId, tenantId, newEndDate}}.</li>
 * </ul>
 *
 * <h2>{@link #MARKET_CAMPAIGN} — {@link IManageMarketCampaignUseCase}</h2>
 * <ul>
 *   <li>{@code CREATE_CAMPAIGN} → {@code createCampaign}. {@code command} = full {@link CreateCampaignCommand} JSON.</li>
 *   <li>{@code ACTIVATE_CAMPAIGN} → {@code activateCampaign}. {@code command} = {@code {id, tenantId}} (id = campaignId).</li>
 *   <li>{@code PAUSE_CAMPAIGN} → {@code pauseCampaign}. {@code command} = {@code {id, tenantId}} (id = campaignId).</li>
 *   <li>{@code TERMINATE_CAMPAIGN} → {@code terminateCampaign}. {@code command} = {@code {id, tenantId}} (id = campaignId).</li>
 *   <li>{@code VALIDATE_PROMO_CODE} → {@code validatePromoCode}. {@code command} = {@code {code, orderId, tenantId}}.</li>
 * </ul>
 *
 * <p>The returned {@code Mono<String>} is the use-case's response DTO serialized to JSON via the
 * shared {@code tntObjectMapper}; {@code tnt-sync-core} persists it as the {@code entity_version}
 * payload so subsequent delta-pulls reflect the authoritative server-side result. An unrecognized
 * {@code commandName}, a malformed envelope, or any use-case error surfaces as a {@code Mono}
 * error, which {@code OfflineQueueDomainService} turns into a failed offline operation — it is
 * never swallowed.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class MarketOfflineOperationApplier implements IOfflineOperationApplier {

    private static final Logger log = LoggerFactory.getLogger(MarketOfflineOperationApplier.class);

    // ── Aggregate-type constants — single source of truth for the 7 Market aggregate types
    //    reachable through the offline-sync push path. The client sets OfflineOperation.aggregateType
    //    to one of these when building a MARKET_COMMAND offline op. ──
    public static final String MARKET_LISTING = "MARKET_LISTING";
    public static final String MARKET_SERVICE_OFFER = "MARKET_SERVICE_OFFER";
    public static final String MARKET_QUOTE_REQUEST = "MARKET_QUOTE_REQUEST";
    public static final String MARKET_ORDER = "MARKET_ORDER";
    public static final String MARKET_PROVIDER_REVIEW = "MARKET_PROVIDER_REVIEW";
    public static final String MARKET_MERCHANT_CONTRACT = "MARKET_MERCHANT_CONTRACT";
    public static final String MARKET_CAMPAIGN = "MARKET_CAMPAIGN";

    private static final Set<String> SUPPORTED_AGGREGATE_TYPES = Set.of(
            MARKET_LISTING, MARKET_SERVICE_OFFER, MARKET_QUOTE_REQUEST, MARKET_ORDER,
            MARKET_PROVIDER_REVIEW, MARKET_MERCHANT_CONTRACT, MARKET_CAMPAIGN);

    private static final String EMPTY_JSON = "{}";

    private final IManageMarketListingUseCase listingUseCase;
    private final IManageServiceOfferUseCase offerUseCase;
    private final IManageQuoteRequestUseCase quoteUseCase;
    private final IManageMarketOrderUseCase orderUseCase;
    private final IManageProviderReviewUseCase reviewUseCase;
    private final IManageMerchantContractUseCase contractUseCase;
    private final IManageMarketCampaignUseCase campaignUseCase;
    private final ObjectMapper objectMapper;

    public MarketOfflineOperationApplier(IManageMarketListingUseCase listingUseCase,
                                          IManageServiceOfferUseCase offerUseCase,
                                          IManageQuoteRequestUseCase quoteUseCase,
                                          IManageMarketOrderUseCase orderUseCase,
                                          IManageProviderReviewUseCase reviewUseCase,
                                          IManageMerchantContractUseCase contractUseCase,
                                          IManageMarketCampaignUseCase campaignUseCase,
                                          @Qualifier("tntObjectMapper") ObjectMapper objectMapper) {
        this.listingUseCase = listingUseCase;
        this.offerUseCase = offerUseCase;
        this.quoteUseCase = quoteUseCase;
        this.orderUseCase = orderUseCase;
        this.reviewUseCase = reviewUseCase;
        this.contractUseCase = contractUseCase;
        this.campaignUseCase = campaignUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String aggregateType) {
        return SUPPORTED_AGGREGATE_TYPES.contains(aggregateType);
    }

    @Override
    public Mono<String> apply(OfflineOperation op) {
        return Mono.defer(() -> {
            try {
                JsonNode envelope = objectMapper.readTree(op.getPayload());
                String commandName = envelope.path("commandName").asText(null);
                if (commandName == null || commandName.isBlank()) {
                    return Mono.error(new IllegalArgumentException(
                            "MARKET_COMMAND payload is missing required 'commandName' field: " + op.getId()));
                }
                JsonNode commandNode = envelope.path("command");
                log.debug("Dispatching MARKET_COMMAND {} for aggregate {}/{}", commandName, op.getAggregateType(), op.getAggregateId());
                return dispatch(commandName, commandNode);
            } catch (JsonProcessingException e) {
                return Mono.error(new IllegalArgumentException(
                        "Invalid MARKET_COMMAND payload JSON for op " + op.getId() + ": " + e.getMessage(), e));
            }
        });
    }

    private Mono<String> dispatch(String commandName, JsonNode commandNode) throws JsonProcessingException {
        return switch (commandName) {
            // ── MarketListing ──
            case "CREATE_LISTING" ->
                    listingUseCase.createListing(readCommand(commandNode, CreateMarketListingCommand.class)).map(this::toJson);
            case "UPDATE_LISTING" -> {
                UpdateListingRequest r = readCommand(commandNode, UpdateListingRequest.class);
                UpdateMarketListingCommand cmd = new UpdateMarketListingCommand(
                        r.displayName(), r.tagline(), r.description(), r.contactEmail(), r.contactPhone(),
                        r.websiteUrl(), r.socialLinks(), r.certificationIds(), r.foundedYear(),
                        r.cities(), r.radiusKm(), r.centerLat(), r.centerLng());
                yield listingUseCase.updateListing(r.listingId(), cmd, r.tenantId()).map(this::toJson);
            }
            case "SUBMIT_LISTING_FOR_REVIEW" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield listingUseCase.submitForReview(r.id(), r.tenantId()).map(this::toJson);
            }
            case "APPROVE_LISTING" -> {
                IdActorTenantRequest r = readCommand(commandNode, IdActorTenantRequest.class);
                yield listingUseCase.approveListing(r.id(), r.actorId(), r.tenantId()).map(this::toJson);
            }
            case "REJECT_LISTING" -> {
                IdActorReasonTenantRequest r = readCommand(commandNode, IdActorReasonTenantRequest.class);
                yield listingUseCase.rejectListing(r.id(), r.actorId(), r.reason(), r.tenantId()).map(this::toJson);
            }
            case "UNPUBLISH_LISTING" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield listingUseCase.unpublishListing(r.id(), r.tenantId()).map(this::toJson);
            }
            case "SUSPEND_LISTING" -> {
                IdReasonTenantRequest r = readCommand(commandNode, IdReasonTenantRequest.class);
                yield listingUseCase.suspendListing(r.id(), r.reason(), r.tenantId()).map(this::toJson);
            }
            case "TRACK_VIEW" -> {
                TrackViewRequest r = readCommand(commandNode, TrackViewRequest.class);
                yield listingUseCase.trackView(r.listingId(), r.tenantId()).then(Mono.just(EMPTY_JSON));
            }
            case "DELETE_LISTING" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield listingUseCase.deleteListing(r.id(), r.tenantId()).then(Mono.just(EMPTY_JSON));
            }

            // ── ServiceOffer ──
            case "CREATE_SERVICE_OFFER" ->
                    offerUseCase.createOffer(readCommand(commandNode, CreateServiceOfferCommand.class)).map(this::toJson);
            case "UPDATE_SERVICE_OFFER" -> {
                UpdateServiceOfferRequest r = readCommand(commandNode, UpdateServiceOfferRequest.class);
                UpdateServiceOfferCommand cmd = new UpdateServiceOfferCommand(
                        r.name(), r.description(), r.serviceType(), r.basePriceXaf(), r.perKmRateXaf(),
                        r.perKgRateXaf(), r.minimumPriceXaf(), r.pricingDslExpression(), r.maxWeightKg(),
                        r.acceptsFragile(), r.acceptsPerishable(), r.daysOfWeek(), r.expressAvailable(), r.sameDayAvailable());
                yield offerUseCase.updateOffer(r.offerId(), cmd, r.tenantId()).map(this::toJson);
            }
            case "ACTIVATE_SERVICE_OFFER" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield offerUseCase.activateOffer(r.id(), r.tenantId()).map(this::toJson);
            }
            case "DEACTIVATE_SERVICE_OFFER" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield offerUseCase.deactivateOffer(r.id(), r.tenantId()).map(this::toJson);
            }
            case "ARCHIVE_SERVICE_OFFER" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield offerUseCase.archiveOffer(r.id(), r.tenantId()).map(this::toJson);
            }

            // ── QuoteRequest ──
            case "CREATE_QUOTE_REQUEST" ->
                    quoteUseCase.createQuoteRequest(readCommand(commandNode, CreateQuoteRequestCommand.class)).map(this::toJson);
            case "SUBMIT_QUOTE_RESPONSE" -> {
                SubmitQuoteResponseRequest r = readCommand(commandNode, SubmitQuoteResponseRequest.class);
                SubmitQuoteResponseCommand cmd = new SubmitQuoteResponseCommand(
                        r.providerId(), r.proposedPriceXaf(), r.estimatedPickupAt(), r.estimatedDeliveryAt(),
                        r.etaHours(), r.message(), r.conditions(), r.validHours());
                yield quoteUseCase.submitQuoteResponse(r.quoteRequestId(), cmd, r.tenantId()).map(this::toJson);
            }
            case "SELECT_QUOTE_RESPONSE" -> {
                SelectQuoteResponseRequest r = readCommand(commandNode, SelectQuoteResponseRequest.class);
                yield quoteUseCase.selectQuoteResponse(r.quoteRequestId(), r.responseId(), r.clientId(), r.tenantId()).map(this::toJson);
            }
            case "REJECT_QUOTE_RESPONSE" -> {
                RejectQuoteResponseCommand r = readCommand(commandNode, RejectQuoteResponseCommand.class);
                yield quoteUseCase.rejectQuoteResponse(r.quoteRequestId(), r.responseId(), r.clientId(), r.tenantId()).map(this::toJson);
            }
            case "CANCEL_QUOTE_REQUEST" -> {
                CancelQuoteRequestRequest r = readCommand(commandNode, CancelQuoteRequestRequest.class);
                yield quoteUseCase.cancelQuoteRequest(r.quoteRequestId(), r.reason(), r.clientId(), r.tenantId()).map(this::toJson);
            }

            // ── MarketOrder ──
            case "PLACE_ORDER" ->
                    orderUseCase.placeOrder(readCommand(commandNode, PlaceMarketOrderCommand.class)).map(this::toJson);
            case "PLACE_ORDER_FROM_QUOTE" ->
                    orderUseCase.placeOrderFromQuote(readCommand(commandNode, PlaceOrderFromQuoteCommand.class)).map(this::toJson);
            case "CONFIRM_ORDER" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield orderUseCase.confirmOrder(r.id(), r.tenantId()).map(this::toJson);
            }
            case "CONFIRM_ORDER_PAYMENT" -> {
                ProcessOrderPaymentRequest r = readCommand(commandNode, ProcessOrderPaymentRequest.class);
                ProcessPaymentCommand cmd = new ProcessPaymentCommand(r.paymentMethod(), r.transactionRef(), r.paidAmountXaf(), r.mobileMoneyPhone());
                yield orderUseCase.processPayment(r.orderId(), cmd, r.tenantId()).map(this::toJson);
            }
            case "DISPATCH_ORDER" -> {
                DispatchOrderRequest r = readCommand(commandNode, DispatchOrderRequest.class);
                yield orderUseCase.dispatchOrder(r.orderId(), r.deliveryMissionId(), r.tenantId()).map(this::toJson);
            }
            case "MARK_ORDER_IN_TRANSIT" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield orderUseCase.markInTransit(r.id(), r.tenantId()).map(this::toJson);
            }
            case "DELIVER_ORDER" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield orderUseCase.markDelivered(r.id(), r.tenantId()).map(this::toJson);
            }
            case "COMPLETE_ORDER" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield orderUseCase.completeOrder(r.id(), r.tenantId()).map(this::toJson);
            }
            case "CANCEL_ORDER" -> {
                IdReasonTenantRequest r = readCommand(commandNode, IdReasonTenantRequest.class);
                yield orderUseCase.cancelOrder(r.id(), r.reason(), r.tenantId()).map(this::toJson);
            }

            // ── ProviderReview ──
            case "CREATE_REVIEW" ->
                    reviewUseCase.submitReview(readCommand(commandNode, SubmitReviewCommand.class)).map(this::toJson);
            case "APPROVE_REVIEW" -> {
                IdActorTenantRequest r = readCommand(commandNode, IdActorTenantRequest.class);
                yield reviewUseCase.approveReview(r.id(), r.actorId(), r.tenantId()).map(this::toJson);
            }
            case "REJECT_REVIEW" -> {
                IdActorReasonTenantRequest r = readCommand(commandNode, IdActorReasonTenantRequest.class);
                yield reviewUseCase.rejectReview(r.id(), r.actorId(), r.reason(), r.tenantId()).map(this::toJson);
            }
            case "FLAG_REVIEW" -> {
                IdReasonTenantRequest r = readCommand(commandNode, IdReasonTenantRequest.class);
                yield reviewUseCase.flagReview(r.id(), r.reason(), r.tenantId()).map(this::toJson);
            }

            // ── MerchantContract ──
            case "CREATE_CONTRACT_NEGOTIATION" ->
                    contractUseCase.initNegotiation(readCommand(commandNode, InitContractNegotiationCommand.class)).map(this::toJson);
            case "SIGN_CONTRACT_BY_MERCHANT" -> {
                IdActorTenantRequest r = readCommand(commandNode, IdActorTenantRequest.class);
                yield contractUseCase.signByMerchant(r.id(), r.actorId(), r.tenantId()).map(this::toJson);
            }
            case "COUNTERSIGN_CONTRACT_BY_PROVIDER" -> {
                IdActorTenantRequest r = readCommand(commandNode, IdActorTenantRequest.class);
                yield contractUseCase.countersignByProvider(r.id(), r.actorId(), r.tenantId()).map(this::toJson);
            }
            case "TERMINATE_CONTRACT" -> {
                IdReasonTenantRequest r = readCommand(commandNode, IdReasonTenantRequest.class);
                yield contractUseCase.terminateContract(r.id(), r.reason(), r.tenantId()).map(this::toJson);
            }
            case "RENEW_CONTRACT" -> {
                RenewContractRequest r = readCommand(commandNode, RenewContractRequest.class);
                RenewContractCommand cmd = new RenewContractCommand(r.newEndDate());
                yield contractUseCase.renewContract(r.contractId(), cmd, r.tenantId()).map(this::toJson);
            }

            // ── MarketCampaign ──
            case "CREATE_CAMPAIGN" ->
                    campaignUseCase.createCampaign(readCommand(commandNode, CreateCampaignCommand.class)).map(this::toJson);
            case "ACTIVATE_CAMPAIGN" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield campaignUseCase.activateCampaign(r.id(), r.tenantId()).map(this::toJson);
            }
            case "PAUSE_CAMPAIGN" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield campaignUseCase.pauseCampaign(r.id(), r.tenantId()).map(this::toJson);
            }
            case "TERMINATE_CAMPAIGN" -> {
                IdTenantRequest r = readCommand(commandNode, IdTenantRequest.class);
                yield campaignUseCase.terminateCampaign(r.id(), r.tenantId()).map(this::toJson);
            }
            case "VALIDATE_PROMO_CODE" -> {
                ValidatePromoCodeRequest r = readCommand(commandNode, ValidatePromoCodeRequest.class);
                yield campaignUseCase.validatePromoCode(r.code(), r.orderId(), r.tenantId()).map(this::toJson);
            }

            default -> Mono.error(new IllegalArgumentException("Unsupported MARKET_COMMAND commandName: " + commandName));
        };
    }

    private <T> T readCommand(JsonNode commandNode, Class<T> type) throws JsonProcessingException {
        return objectMapper.treeToValue(commandNode, type);
    }

    private String toJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Market use-case response to JSON", e);
        }
    }

    // ── Per-command envelope DTOs — capture exactly the extra scalar parameters (id path
    //    variables, tenantId, actor ids) that the matching IManage*UseCase method needs beyond
    //    (or instead of) an existing application/port/in/command/*.java DTO, since there is no
    //    HTTP path/header to carry them through the offline-sync push path. Several dispatch
    //    cases share a shape (e.g. simple "id + tenantId") and reuse the same record. ──

    private record IdTenantRequest(UUID id, String tenantId) {}

    private record IdActorTenantRequest(UUID id, UUID actorId, String tenantId) {}

    private record IdActorReasonTenantRequest(UUID id, UUID actorId, String reason, String tenantId) {}

    private record IdReasonTenantRequest(UUID id, String reason, String tenantId) {}

    private record TrackViewRequest(UUID listingId, String tenantId) {}

    private record UpdateListingRequest(
            UUID listingId, String tenantId,
            String displayName, String tagline, String description, String contactEmail, String contactPhone,
            String websiteUrl, Map<String, String> socialLinks, List<String> certificationIds, Integer foundedYear,
            List<String> cities, Double radiusKm, Double centerLat, Double centerLng) {}

    private record UpdateServiceOfferRequest(
            UUID offerId, String tenantId,
            String name, String description, ServiceType serviceType,
            Long basePriceXaf, Long perKmRateXaf, Long perKgRateXaf, Long minimumPriceXaf, String pricingDslExpression,
            Double maxWeightKg, Boolean acceptsFragile, Boolean acceptsPerishable,
            Set<DayOfWeek> daysOfWeek, Boolean expressAvailable, Boolean sameDayAvailable) {}

    private record SubmitQuoteResponseRequest(
            UUID quoteRequestId, String tenantId,
            UUID providerId, long proposedPriceXaf, LocalDateTime estimatedPickupAt, LocalDateTime estimatedDeliveryAt,
            double etaHours, String message, List<String> conditions, int validHours) {}

    private record SelectQuoteResponseRequest(UUID quoteRequestId, UUID responseId, UUID clientId, String tenantId) {}

    private record CancelQuoteRequestRequest(UUID quoteRequestId, String reason, UUID clientId, String tenantId) {}

    private record ProcessOrderPaymentRequest(
            UUID orderId, String tenantId,
            PaymentMethod paymentMethod, String transactionRef, long paidAmountXaf, String mobileMoneyPhone) {}

    private record DispatchOrderRequest(UUID orderId, String deliveryMissionId, String tenantId) {}

    private record RenewContractRequest(UUID contractId, String tenantId, LocalDate newEndDate) {}

    private record ValidatePromoCodeRequest(String code, UUID orderId, String tenantId) {}
}
