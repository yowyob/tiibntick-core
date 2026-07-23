package com.yowyob.tiibntick.common.kafka;

/**
 * Single frozen referential of Kafka topic names used across TiiBnTick Core.
 *
 * <p><b>Convention (Audit n°5 · P-03 / P-12):</b> {@code tnt.<module>.<entity>.<action>},
 * lower-case, dot-separated. New topics MUST follow this convention and MUST be added here
 * first — no module should declare a topic-name string literal of its own.
 *
 * <p><b>Migration note:</b> before this class existed, topic names were duplicated as private
 * {@code static final String} constants (or bare literals) in ~20 producer/consumer classes,
 * which is exactly how the producer/consumer naming mismatches catalogued in Audit n°5 §3.1
 * happened in the first place (e.g. {@code mission.status-changed} vs
 * {@code mission.status.changed}, fixed in a prior commit of this same chantier). A handful of
 * legacy topics below still use a hyphen or underscore instead of a dot
 * ({@link #DELIVERY_PACKAGE_PICKED_UP}, {@link #BILLING_WALLET_PAYMENT_INITIATED}, etc.) — renaming
 * those requires coordinating every producer and consumer of that exact topic at once, which
 * is tracked as a separate follow-up (see {@code docs/audits/remediation/phase-0-critical.md},
 * Chantier C) rather than folded into this centralization pass. Do not introduce new
 * non-dot topics.
 *
 * <p>This class intentionally has no dependency on any business module — it is pure data,
 * placed in {@code tnt-common-core} (L1) so every module, at any layer, can depend down into
 * it without violating the layering rule.
 *
 * @author MANFOUO Braun
 */
public final class TntTopics {

    private TntTopics() {
    }

    // ── Delivery (tnt-delivery-core, L3) ────────────────────────────────────────

    /** Catch-all envelope topic for delivery domain events with no dedicated topic. */
    public static final String DELIVERY_EVENTS = "tnt.delivery.events";
    /** Mission/delivery status transitions. Consumed by tnt-incident-core, tnt-sync-core,
     *  tnt-realtime-core, tnt-market-back-core. */
    public static final String DELIVERY_MISSION_STATUS_CHANGED = "tnt.delivery.mission.status.changed";
    /** FreelancerOrg assigned to a delivery. Consumed by tnt-notify-core, tnt-accounting-core,
     *  tnt-billing-wallet. */
    public static final String DELIVERY_FREELANCER_ORG_ASSIGNED = "tnt.delivery.freelancer_org.assigned";
    /** Published by {@code KafkaDeliveryEventPublisher} on {@code DeliveryCreatedEvent}. Consumed
     *  by coreBackend's agency-assignment (property core-mission-created). Fixed 2026-07-23 —
     *  this Javadoc previously said "never published" (Audit n5 P-01, documented debt). */
    public static final String DELIVERY_MISSION_CREATED = "tnt.delivery.mission.created";
    /** Published by {@code KafkaDeliveryEventPublisher} on {@code DeliveryCompletedEvent}.
     *  Consumed by tnt-billing-invoice, tnt-tp-core, tnt-sales-core. Fixed 2026-07-23 — this
     *  Javadoc previously said "never published" (Audit n5 P-01, documented debt). */
    public static final String DELIVERY_MISSION_COMPLETED = "tnt.delivery.mission.completed";
    /** Published by {@code KafkaDeliveryEventPublisher} on {@code ParcelPickedUpEvent} (physical
     *  pickup = fulfillment start). Consumed by tnt-sales-core. Fixed 2026-07-23 — this Javadoc
     *  previously said "never published" (Audit n5 P-01, documented debt). */
    public static final String DELIVERY_MISSION_STARTED = "tnt.delivery.mission.started";
    /** Published by {@code KafkaDeliveryEventPublisher} on {@code DeliveryFailedEvent}. Consumed
     *  by tnt-sales-core. Fixed 2026-07-23 — this Javadoc previously said "never published"
     *  (Audit n5 P-01, documented debt). */
    public static final String DELIVERY_MISSION_FAILED = "tnt.delivery.mission.failed";
    /** Published by {@code KafkaDeliveryEventPublisher} on {@code DeliveryCompletedEvent}, in
     *  addition to {@link #DELIVERY_MISSION_COMPLETED} — same real-world occurrence, different
     *  consumer group. Consumed by coreBackend's agency-assignment (property
     *  core-package-delivered). Fixed 2026-07-23 — this Javadoc previously said "never
     *  published" (Audit n5 P-01, documented debt). */
    public static final String DELIVERY_PACKAGE_DELIVERED = "tnt.delivery.package.delivered";
    /** Published by {@code KafkaDeliveryEventPublisher} alongside every package-state-changing
     *  event (pickup, transit, relay stop, mission status change, completion). Consumed by
     *  tnt-sync-core. Fixed 2026-07-23 — this Javadoc previously said "never published"
     *  (Audit n5 P-01, documented debt). */
    public static final String DELIVERY_PACKAGE_UPDATED = "tnt.delivery.package.updated";
    /** Legacy hyphenated name — provisioning bean only, no producer/consumer wired yet. */
    public static final String DELIVERY_PACKAGE_PICKED_UP = "tnt.delivery.package.picked-up";
    /** Legacy hyphenated name — provisioning bean only, no producer/consumer wired yet. */
    public static final String DELIVERY_HUB_DEPOSIT_CREATED = "tnt.delivery.hub-deposit.created";
    /** Legacy hyphenated name — provisioning bean only, no producer/consumer wired yet. */
    public static final String DELIVERY_HUB_DEPOSIT_PICKED_UP = "tnt.delivery.hub-deposit.picked-up";
    /** Dispute has frozen/released a package. Produced by tnt-dispute-core's DeliveryStatusAdapter. */
    public static final String DELIVERY_PACKAGE_DISPUTED = "tnt.delivery.package.disputed";
    public static final String DELIVERY_PACKAGE_DISPUTE_RELEASED = "tnt.delivery.package.dispute.released";

    // ── Incident (tnt-incident-core, L3) ────────────────────────────────────────

    public static final String INCIDENT_CREATED = "tnt.incident.created";
    public static final String INCIDENT_STATUS_CHANGED = "tnt.incident.status.changed";
    public static final String INCIDENT_TRIAGED = "tnt.incident.triaged";
    public static final String INCIDENT_DRIVER_ASSIGNED = "tnt.incident.driver.assigned";
    public static final String INCIDENT_HANDOVER_COMPLETED = "tnt.incident.handover.completed";
    /** Consumed by tnt-delivery-core, tnt-actor-core. */
    public static final String INCIDENT_RESOLVED = "tnt.incident.resolved";
    /** Consumed by tnt-delivery-core, tnt-actor-core. */
    public static final String INCIDENT_CLOSED = "tnt.incident.closed";
    public static final String INCIDENT_CANCELLED = "tnt.incident.cancelled";
    public static final String INCIDENT_ESCALATED = "tnt.incident.escalated";
    /** Consumed by tnt-dispute-core, tnt-billing-wallet (definitive payment freeze). */
    public static final String INCIDENT_ESCALATED_TO_DISPUTE = "tnt.incident.escalated.to.dispute";
    public static final String INCIDENT_INTERAGENCY_REQUESTED = "tnt.incident.interagency.requested";
    public static final String INCIDENT_INTERAGENCY_COMPLETED = "tnt.incident.interagency.completed";

    // ── Realtime (tnt-realtime-core, L3) ────────────────────────────────────────

    /** Enriched GPS position events. Consumed by tnt-incident-core. */
    public static final String REALTIME_GPS_POSITION_UPDATED = "tnt.realtime.gps.position.updated";
    /** Geofence breach events. Consumed by tnt-incident-core, tnt-sync-core. */
    public static final String REALTIME_GEOFENCE_TRIGGERED = "tnt.realtime.geofence.triggered";
    public static final String REALTIME_ETA_UPDATED = "tnt.realtime.eta.updated";
    public static final String REALTIME_ACTOR_CONNECTED = "tnt.realtime.actor.connected";
    public static final String REALTIME_ACTOR_DISCONNECTED = "tnt.realtime.actor.disconnected";
    public static final String REALTIME_PRESENCE_CHANGED = "tnt.realtime.presence.changed";

    // ── Route (tnt-route-core, L3) ───────────────────────────────────────────────

    public static final String ROUTE_TOUR_EVENTS = "tnt.route.tour.events";
    /** Consumed by tnt-realtime-core's ETAUpdateEventConsumer. */
    public static final String ROUTE_ETA_UPDATED = "tnt.route.eta.updated";
    public static final String ROUTE_REROUTE_EVENTS = "tnt.route.reroute.events";
    public static final String ROUTE_VRP_EVENTS = "tnt.route.vrp.events";

    // ── Geo (tnt-geo-core, L3) ───────────────────────────────────────────────────

    /** Legacy hyphenated name — provisioning bean only (distinct from the realtime module's
     *  own {@link #REALTIME_GPS_POSITION_UPDATED}). */
    public static final String GEO_GPS_POSITION_UPDATED = "tnt.geo.gps.position-updated";
    public static final String GEO_TRAFFIC_EVENTS = "tnt.geo.traffic.events";
    public static final String GEO_NODE_EVENTS = "tnt.geo.node.events";
    public static final String GEO_ZONE_EVENTS = "tnt.geo.zone.events";
    /** Published by {@code KafkaGeoEventPublisher} whenever a {@code TrafficConditionChangedEvent}
     *  reaches it — that only happens when {@code TrafficConditionChangedEvent#isSignificant()}
     *  was already true (&gt;20% congestion swing), the domain's own existing definition of
     *  alert-worthy. Consumed by tnt-sync-core. Fixed 2026-07-23 — this Javadoc previously said
     *  no "alert" concept existed in tnt-geo-core at all (Audit n5 P-01, documented debt); it
     *  did, just not exposed as its own topic. */
    public static final String GEO_ALERT_CREATED = "tnt.geo.alert.created";

    // ── Sync (tnt-sync-core, L3) ─────────────────────────────────────────────────

    public static final String SYNC_ENTITY_VERSION_CHANGED = "tnt.sync.entity.version.changed";
    public static final String SYNC_CONFLICT_DETECTED = "tnt.sync.conflict.detected";
    public static final String SYNC_COMPLETED = "tnt.sync.completed";
    public static final String SYNC_DELTA_REQUESTED = "tnt.sync.delta.requested";
    /** Legacy hyphenated DLQ topic name for tnt-sync-core's poison-pill recoverer. */
    public static final String SYNC_ENTITY_CHANGED_DLQ = "tnt.sync.entity-changed.dlq";

    // ── Dispute (tnt-dispute-core, L3) ───────────────────────────────────────────

    public static final String DISPUTE_OPENED = "tnt.dispute.opened";
    public static final String DISPUTE_STATUS_CHANGED = "tnt.dispute.status.changed";
    /** Provisioning-bean-only name, distinct from {@link #DISPUTE_STATUS_CHANGED} — no wired
     *  producer/consumer under this exact name. */
    public static final String DISPUTE_RESOLVED = "tnt.dispute.resolved";
    public static final String DISPUTE_EVIDENCE_SUBMITTED = "tnt.dispute.evidence.submitted";
    public static final String DISPUTE_RULED = "tnt.dispute.ruled";
    public static final String DISPUTE_ESCALATED = "tnt.dispute.escalated";
    public static final String DISPUTE_COMPENSATION_PROCESSED = "tnt.dispute.compensation.processed";
    public static final String DISPUTE_CLOSED = "tnt.dispute.closed";
    /** Legacy hyphenated names — provisioning beans only (Audit n5, no wired producer/consumer). */
    public static final String DISPUTE_EVIDENCE_ADDED = "tnt.dispute.evidence-added";
    public static final String DISPUTE_REFUND_INITIATED = "tnt.dispute.refund-initiated";
    public static final String DISPUTE_COMPENSATION_PAID = "tnt.dispute.compensation-paid";
    /** Produced by tnt-dispute-core's BillingCompensationAdapter. Consumed by
     *  tnt-billing-wallet's {@code WalletBillingEventConsumer#onCompensationInitiated}, which
     *  pays out {@code WALLET_CREDIT} compensations and confirms on
     *  {@link #BILLING_COMPENSATION_PAID}. Fixed 2026-07-23 — this Javadoc previously said the
     *  dispute-to-wallet loop was only half-wired (Audit n5 P-01, documented debt); other
     *  compensation methods ({@code MOBILE_MONEY_*}, {@code BANK_TRANSFER},
     *  {@code SERVICE_CREDIT}, {@code REDELIVERY}) still have no automated payout and are logged
     *  for manual settlement instead. */
    public static final String BILLING_COMPENSATION_INITIATED = "tnt.billing.compensation.initiated";
    /** Published by tnt-billing-wallet's {@code WalletBillingEventConsumer} after a
     *  {@code WALLET_CREDIT} compensation payout succeeds. Consumed by tnt-dispute-core's
     *  {@code DisputeEventConsumer}, which transitions the dispute to {@code COMPENSATED}. Fixed
     *  2026-07-23 — see {@link #BILLING_COMPENSATION_INITIATED} (Audit n5 P-01, documented debt). */
    public static final String BILLING_COMPENSATION_PAID = "tnt.billing.compensation.paid";

    // ── Notify (tnt-notify-core, L3) ─────────────────────────────────────────────

    public static final String NOTIFY_NOTIFICATION_REQUESTED = "tnt.notify.notification.requested";
    public static final String NOTIFY_NOTIFICATION_DELIVERED = "tnt.notify.notification.delivered";
    public static final String NOTIFY_NOTIFICATION_FAILED = "tnt.notify.notification.failed";

    // ── Media (tnt-media-core, L3) ───────────────────────────────────────────────

    public static final String MEDIA_FILE_UPLOADED = "tnt.media.file.uploaded";
    public static final String MEDIA_FILE_DELETED = "tnt.media.file.deleted";
    /** Published by FreelancerOrgAdminService (tnt-administration-core) via
     *  TntAdministrationEventPublisherAdapter's outbox-backed publish() (Chantier C P5); consumed
     *  by tnt-notify-core. Fixed 2026-07-23 — this Javadoc previously said "never published"
     *  (Audit n5 P-01, documented debt), that is no longer accurate. */
    public static final String ADMIN_FREELANCER_ORG_KYC_APPROVED = "tnt.admin.freelancer_org.kyc_approved";
    public static final String ADMIN_FREELANCER_ORG_KYC_REJECTED = "tnt.admin.freelancer_org.kyc_rejected";
    public static final String ADMIN_FREELANCER_ORG_SUSPENDED = "tnt.admin.freelancer_org.suspended";
    public static final String ADMIN_FREELANCER_ORG_UNSUSPENDED = "tnt.admin.freelancer_org.unsuspended";
    public static final String ADMIN_FREELANCER_ORG_BLACKLISTED = "tnt.admin.freelancer_org.blacklisted";

    // ── Actor / Identity (tnt-actor-core, L2) ────────────────────────────────────

    public static final String ACTOR_STATUS_CHANGED = "tnt.actor.status.changed";
    public static final String ACTOR_LOCATION_UPDATED = "tnt.actor.location.updated";
    public static final String ACTOR_BADGE_EARNED = "tnt.actor.badge.earned";
    /** Consumed by agency-workforce's ActorKycConsumer (property core-actor-kyc, Audit n5 P-01 fix). */
    public static final String ACTOR_KYC_VALIDATED = "tnt.actor.kyc.validated";
    public static final String ACTOR_MISSION_ASSIGNED = "tnt.actor.mission.assigned";
    /** Published by {@code KafkaActorEventPublisher} on {@code ActorProfileUpdatedEvent},
     *  emitted after every profile mutation already wired end-to-end elsewhere: KYC
     *  submission/validation ({@code ActorKycService}), rating ({@code ActorRatingService}),
     *  badges ({@code ActorBadgeService}), client loyalty points
     *  ({@code ClientProfileService#addLoyaltyPoints}). Consumed by tnt-sync-core. Fixed
     *  2026-07-23 — this Javadoc previously said no update mutation existed anywhere in
     *  tnt-actor-core (Audit n5 P-01, documented debt); the mutations existed and were already
     *  persisted, they just never announced themselves on this topic. */
    public static final String ACTOR_PROFILE_UPDATED = "tnt.actor.profile.updated";
    public static final String ACTOR_REPUTATION_CHANGED = "tnt.actor.reputation.changed";
    /** Published by tnt-organization-core's FreelancerOrgEventPublisherAdapter via the outbox
     *  (Chantier C P5, fixed 2026-07-23 — it was previously publishing through a dead
     *  ApplicationEventPublisher with no listener, silently losing every event; see the P5
     *  inventory doc). Consumed by actor-core's FreelancerOrgEventConsumer. This Javadoc
     *  previously said "never published" (Audit n5 P-01, documented debt), no longer accurate. */
    public static final String FREELANCER_ORG_CREATED = "tnt.freelancer_org.created";
    public static final String FREELANCER_ORG_SUB_DELIVERER_ASSOCIATED = "tnt.freelancer_org.sub_deliverer.associated";
    public static final String FREELANCER_ORG_SUB_DELIVERER_REVOKED = "tnt.freelancer_org.sub_deliverer.revoked";
    public static final String FREELANCER_ORG_VERIFIED = "tnt.freelancer_org.verified";

    // ── Third-Party / Client (tnt-tp-core, L2) ───────────────────────────────────

    public static final String TP_CLIENT_PROFILE_EVENTS = "tnt.tp.client.profile.events";
    public static final String TP_KYC_EVENTS = "tnt.tp.kyc.events";
    public static final String TP_LOYALTY_EVENTS = "tnt.tp.loyalty.events";
    public static final String TP_PHONE_ALIAS_EVENTS = "tnt.tp.phone.alias.events";
    public static final String TP_RATING_EVENTS = "tnt.tp.rating.events";
    public static final String TP_UNKNOWN_EVENTS = "tnt.tp.unknown.events";

    // ── Administration (tnt-administration-core, L2) ────────────────────────────

    public static final String ADMINISTRATION_EVENTS = "tnt.administration.events";

    // ── Organization (tnt-organization-core, L2) ─────────────────────────────────

    /** Published by {@code HubEventPublisherAdapter} on {@code HubRelaisUpdatedEvent}, emitted
     *  by {@code HubRelaisService#updateCapacity/assignOperator/suspendHub/resumeHub}. Consumed
     *  by tnt-sync-core. Fixed 2026-07-23 — this Javadoc previously said no update/patch
     *  operation existed on {@code HubRelais} (Audit n5 P-01, documented debt); the domain
     *  aggregate already had {@code updateCapacity}/{@code assignOperator}/{@code suspend}/
     *  {@code resume} methods, they were simply never exposed through any use case. */
    public static final String ORGANIZATION_HUB_UPDATED = "tnt.organization.hub.updated";

    // ── Resource (tnt-resource-core, L4) ─────────────────────────────────────────

    /** 6-partitioned (high-throughput fleet tracking). Consumed by tnt-delivery-core's
     *  FreelancerVehicleEventConsumer. Legacy underscore name. */
    public static final String VEHICLE_ASSIGNED_TO_MISSION = "tnt.vehicle.assigned_to_mission";
    public static final String VEHICLE_RELEASED_FROM_MISSION = "tnt.vehicle.released_from_mission";
    public static final String RESOURCE_FREELANCER_VEHICLE_REGISTERED = "tnt.resource.freelancer.vehicle.registered";

    // ── Product / Inventory / Sales (tnt-product-core / tnt-inventory-core / tnt-sales-core, L4) ──

    public static final String PRODUCT_CREATED = "tnt.product.created";
    public static final String PRODUCT_OFFER_PUBLISHED = "tnt.product.offer.published";
    /** Consumed by tnt-agency-org-core's InventoryHubConsumer. */
    public static final String INVENTORY_HUB_PACKAGE_DEPOSITED = "tnt.inventory.hub.package.deposited";
    public static final String INVENTORY_HUB_PACKAGE_PICKEDUP = "tnt.inventory.hub.package.pickedup";
    public static final String INVENTORY_STOCK_LOW = "tnt.inventory.stock.low";
    public static final String SALES_ORDER_CONFIRMED = "tnt.sales.order.confirmed";
    public static final String SALES_ORDER_DISPATCHED = "tnt.sales.order.dispatched";
    public static final String SALES_ORDER_DELIVERED = "tnt.sales.order.delivered";
    public static final String SALES_ORDER_CANCELLED = "tnt.sales.order.cancelled";

    // ── Accounting (tnt-accounting-core, L4) ─────────────────────────────────────

    public static final String ACCOUNTING_JOURNAL_ENTRY_POSTED = "tnt.accounting.journal-entry.posted";
    public static final String ACCOUNTING_PERIOD_CLOSED = "tnt.accounting.period.closed";

    // ── Billing invoice (tnt-billing-invoice, L5) ────────────────────────────────

    /** Envelope topic. Consumed by tnt-billing-report's InvoiceEventReportConsumer. */
    public static final String BILLING_INVOICE_EVENTS = "tnt.billing.invoice.events";
    /** Published by {@code KafkaInvoiceEventPublisher} on {@code InvoicePaid}, in addition to the
     *  catch-all {@link #BILLING_INVOICE_EVENTS}. Consumed by tnt-accounting-core. Fixed
     *  2026-07-23 — this Javadoc previously said "never published" (Audit n5 P-01, documented
     *  debt). */
    public static final String BILLING_INVOICE_PAID = "tnt.billing.invoice.paid";
    /** Provisioning bean only — no wired producer/consumer under this exact name. */
    public static final String BILLING_INVOICE_CREATED = "tnt.billing.invoice.created";
    /** Provisioning bean only (distinct from the wallet module's own hyphenated
     *  {@link #BILLING_WALLET_PAYMENT_FAILED}) — no wired producer/consumer. */
    public static final String BILLING_PAYMENT_FAILED = "tnt.billing.payment.failed";

    // ── Billing wallet (tnt-billing-wallet, L5) ──────────────────────────────────

    /** Legacy hyphenated name — no known consumer, kept as-is (see class Javadoc). */
    public static final String BILLING_WALLET_PAYMENT_INITIATED = "tnt.billing.wallet.payment-initiated";
    /** Consumed by tnt-billing-invoice, tnt-accounting-core, tnt-market-back-core
     *  (Audit n5 P-01 fix — was tnt.billing.wallet.payment-confirmed / tnt.billing.payment.confirmed). */
    public static final String BILLING_WALLET_PAYMENT_CONFIRMED = "tnt.billing.wallet.payment.confirmed";
    /** Legacy hyphenated name — no known consumer, kept as-is (see class Javadoc). */
    public static final String BILLING_WALLET_PAYMENT_FAILED = "tnt.billing.wallet.payment-failed";
    /** Legacy hyphenated name — no known consumer, kept as-is (see class Javadoc). */
    public static final String BILLING_WALLET_WALLET_CREDITED = "tnt.billing.wallet.wallet-credited";
    /** Legacy hyphenated name — no known consumer, kept as-is (see class Javadoc). */
    public static final String BILLING_WALLET_WALLET_DEBITED = "tnt.billing.wallet.wallet-debited";
    /** Provisioning-bean-only name that does NOT match {@link #BILLING_WALLET_WALLET_CREDITED}
     *  (yet another P-01-shaped drift — no consumer either way, so left as a documented
     *  discrepancy rather than folded into the P-01 producer/consumer rename). */
    public static final String BILLING_WALLET_CREDITED_PROVISIONING_BEAN = "tnt.billing.wallet.credited";
    /** See {@link #BILLING_WALLET_CREDITED_PROVISIONING_BEAN}. */
    public static final String BILLING_WALLET_DEBITED_PROVISIONING_BEAN = "tnt.billing.wallet.debited";
    /** Consumed by tnt-billing-wallet itself (intra-module) and tnt-accounting-core
     *  (Audit n5 P-01 fix — was tnt.billing.wallet.commission-calculated / tnt.billing.commission.calculated). */
    public static final String BILLING_WALLET_COMMISSION_CALCULATED = "tnt.billing.wallet.commission.calculated";
    /** Published by {@code WalletKafkaPublisher} on {@code WalletSplitExecuted}, emitted by
     *  {@code WalletService.splitMissionRevenue()}. Consumed by tnt-accounting-core. Fixed
     *  2026-07-23 — this Javadoc previously said "never published" (Audit n5 P-01, documented
     *  debt). */
    public static final String BILLING_WALLET_SPLIT_EXECUTED = "tnt.billing.wallet.split_executed";
    /** Superseded 2026-07-23 (Audit n5 P-01) — tnt-agency-commission's WalletPayoutConsumer
     *  used to listen here expecting a multiplexed "eventType" discriminator payload that
     *  wallet never produced (wallet only knows 6 unitary event topics, none shaped like that).
     *  The consumer now listens on {@link #BILLING_WALLET_PAYMENT_CONFIRMED} instead, reading the
     *  agency-side commissionId back out of that event's {@code invoiceId} field (see
     *  {@code WalletCoreClient}, which smuggles it in on the way out). Kept declared — no
     *  producer or consumer references this topic name anymore, retained only as a historical
     *  marker so the string isn't silently reintroduced. */
    public static final String BILLING_WALLET_EVENTS = "tnt.billing.wallet.events";

    // ── Billing templates (tnt-billing-templates, L5) ───────────────────────────

    /** Consumed by tnt-notify-core, tnt-billing-report. */
    public static final String BILLING_TEMPLATE_APPLIED = "tnt.billing.template.applied";
    public static final String BILLING_CUSTOM_TEMPLATE_SAVED = "tnt.billing.custom_template.saved";

    // ── Market (tnt-market-back-core, coreBackend) ───────────────────────────────

    public static final String MARKET_LISTING_PUBLISHED = "tnt.market.listing.published";
    public static final String MARKET_LISTING_APPROVED = "tnt.market.listing.approved";
    public static final String MARKET_LISTING_REJECTED = "tnt.market.listing.rejected";
    public static final String MARKET_MERCHANT_CONTRACT_SIGNED = "tnt.market.merchant.contract.signed";
    public static final String MARKET_ORDER_CREATED = "tnt.market.order.created";
    public static final String MARKET_ORDER_COMPLETED = "tnt.market.order.completed";
    public static final String MARKET_ORDER_PAID = "tnt.market.order.paid";
    public static final String MARKET_PROVIDER_REVIEW_PUBLISHED = "tnt.market.provider.review.published";
    public static final String MARKET_QUOTE_REQUEST_CREATED = "tnt.market.quote.request.created";
    public static final String MARKET_QUOTE_RESPONSE_SUBMITTED = "tnt.market.quote.response.submitted";

    // ── Go-Freelancer-Point (tnt-go-freelancer-point-back-core, coreBackend) ────
    // Deliberately out of the "tnt.*" convention — flagged in Audit n5 P-12, no consumer,
    // kept as-is pending a decision on whether GOFP should adopt the tnt.* prefix.

    public static final String GOFP_ANNOUNCEMENT_PUBLISHED = "gofp.announcement.published";
    public static final String GOFP_DELIVERY_COMPLETED = "gofp.delivery.completed";
    public static final String GOFP_SUBSCRIPTION_SUSPENDED = "gofp.subscription.suspended";

    // ── Agency (tnt-agency-eventing-core / tnt-agency-compliance-core, coreBackend) ──

    public static final String AGENCY_EVENTS = "tnt.agency.events";
    /** Produced but not consumed by any Core module — Agency mission creation is actually
     *  driven through the platform HTTP gateway, not this topic (Audit n5 §3.3). */
    public static final String AGENCY_MISSION_REQUEST = "tnt.agency.mission.request";
    public static final String AGENCY_STAFF_EVENTS = "tnt.agency.staff.events";
    public static final String AGENCY_CONTRACT_EVENTS = "tnt.agency.contract.events";

    // ── Roles (tnt-roles-core, L1) ───────────────────────────────────────────────

    /** Published by tnt-roles-core's {@code PermissionChangeEventPublisher} on every role
     *  assignment/revocation. Consumed by {@code PermissionCacheInvalidationListener} (same
     *  module, every instance). Fixed 2026-07-23 — this Javadoc previously said "never
     *  published" (Audit n5 P-01/P-20, documented debt); the TTL-based self-expiry remains as
     *  a fallback for any instance that misses the broadcast. */
    public static final String ROLES_PERMISSION_CHANGED = "tnt.roles.permission-changed";

    // ── Trust (tnt-trust-core, L6) — external Kernel contract, not renamed by this referential ──

    public static final String TRUST_EVENTS = "yow.trust.events";
    public static final String TRUST_EVENTS_COMMITTED = "yow.trust.events.committed";

    // ── Cross-cutting infrastructure ─────────────────────────────────────────────

    /** Declared (single partition, transactional ordering) but no outbox writer/reader exists
     *  anywhere in the repo yet (Audit n5 P-05, separate chantier). */
    public static final String OUTBOX_EVENTS = "tnt.outbox.events";
    /** Declared but never used as a DeadLetterPublishingRecoverer target outside tnt-sync-core's
     *  own module-local DLQ (Audit n5 P-04, separate chantier). */
    public static final String DLQ = "tnt.dlq";
}
