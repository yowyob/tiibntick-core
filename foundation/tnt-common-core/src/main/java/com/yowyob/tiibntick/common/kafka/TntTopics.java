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
 * ({@link #BILLING_WALLET_PAYMENT_INITIATED}, etc.) — renaming
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

    // ── Aggregate topics (decision [G.1] of 2026-11-26, cible-evenements-colis-livraison.md §3.1) ──
    // One topic per aggregate; key = aggregate id; body = TntAggregateEvent envelope (§3.3). Declared
    // by TntKafkaTopicsConfig (cleanup.policy=delete, retention tnt.kafka.aggregate-retention-ms).
    // See TntAggregateType for the aggregate -> topic mapping.

    /** Delivery aggregate ({@code DeliveryStatus}, ETA, courier, alerts, payment state) — producer
     *  tnt-delivery-core. */
    public static final String DELIVERY_EVENTS = "tnt.delivery.events";
    /** Parcel aggregate ({@code ParcelStatus}, custody, relay deposits/withdrawals) — producer tnt-delivery-core (lot L2). */
    public static final String PARCEL_EVENTS = "tnt.parcel.events";
    /** Incident aggregate — producer tnt-incident-core (lot L3). */
    public static final String INCIDENT_EVENTS = "tnt.incident.events";
    /** Dispute aggregate — producer tnt-dispute-core (lot L4). */
    public static final String DISPUTE_EVENTS = "tnt.dispute.events";
    /** Announcement aggregate and its courier responses — producer tnt-delivery-core (lot L2). */
    public static final String ANNOUNCEMENT_EVENTS = "tnt.announcement.events";

    // Lot L8 (G.5): the former per-event topics of these aggregates (tnt.delivery.mission.*, tnt.delivery.package.*,
    // tnt.delivery.hub-deposit.*, tnt.delivery.announcement.*, tnt.delivery.freelancer_org.assigned, tnt.incident.<event>,
    // tnt.dispute.<event>, tnt.inventory.hub.package.*, tnt.sync.entity-changed.dlq) are gone: every module reads the
    // aggregate topics above and selects on eventType / the status transition.

    // ── Realtime (tnt-realtime-core, L3) ────────────────────────────────────────

    /** Enriched GPS position events. Consumed by tnt-incident-core. */
    public static final String REALTIME_GPS_POSITION_UPDATED = "tnt.realtime.gps.position.updated";
    /** Geofence breach events. Consumed by tnt-incident-core, tnt-sync-core. */
    public static final String REALTIME_GEOFENCE_TRIGGERED = "tnt.realtime.geofence.triggered";
    public static final String REALTIME_ETA_UPDATED = "tnt.realtime.eta.updated";
    public static final String REALTIME_ACTOR_CONNECTED = "tnt.realtime.actor.connected";
    public static final String REALTIME_ACTOR_DISCONNECTED = "tnt.realtime.actor.disconnected";

    // ── Route (tnt-route-core, L3) ───────────────────────────────────────────────

    public static final String ROUTE_TOUR_EVENTS = "tnt.route.tour.events";
    /** Consumed by tnt-realtime-core's ETAUpdateEventConsumer. */
    public static final String ROUTE_ETA_UPDATED = "tnt.route.eta.updated";
    public static final String ROUTE_REROUTE_EVENTS = "tnt.route.reroute.events";
    public static final String ROUTE_VRP_EVENTS = "tnt.route.vrp.events";

    // ── Geo (tnt-geo-core, L3) ───────────────────────────────────────────────────

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

    public static final String SYNC_CONFLICT_DETECTED = "tnt.sync.conflict.detected";
    public static final String SYNC_COMPLETED = "tnt.sync.completed";

    // ── Dispute (tnt-dispute-core, L3) ───────────────────────────────────────────

    /** Published by tnt-billing-wallet ({@code CompensationOutboxEventPublisher}, lot L5) once the Kernel
     *  compensation fund has paid a dispute compensation ({@code POST /api/payments/compensations}, spec §4.3):
     *  {@code disputeId}, {@code compensationId}, {@code paymentReference}, {@code beneficiaryActorId},
     *  {@code amount}, {@code currency}, {@code fundWalletId}. Consumed by tnt-dispute-core's
     *  {@code DisputeEventConsumer} (LT9 when the synchronous payment's dispute transaction did not commit; a no-op
     *  when the dispute is already COMPENSATED). {@code tnt.billing.compensation.initiated} is gone (lot L5): the
     *  payment is a synchronous call of {@code IDisputeCompensationPort}. */
    public static final String BILLING_COMPENSATION_PAID = "tnt.billing.compensation.paid";

    // ── Notify (tnt-notify-core, L3) ─────────────────────────────────────────────

    /** Lot L8 (annex 9.1): literals of {@code NotifyProperties} made constants; outbound, no internal consumer. */
    public static final String NOTIFY_NOTIFICATIONS_SENT = "tnt.notifications.sent";
    public static final String NOTIFY_NOTIFICATIONS_FAILED = "tnt.notifications.failed";


    // ── Media (tnt-media-core, L3) ───────────────────────────────────────────────

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
    /** Published by tnt-organization-core's FreelancerOrgEventPublisherAdapter via the outbox
     *  (Chantier C P5, fixed 2026-07-23 — it was previously publishing through a dead
     *  ApplicationEventPublisher with no listener, silently losing every event; see the P5
     *  inventory doc). Consumed by actor-core's FreelancerOrgEventConsumer. This Javadoc
     *  previously said "never published" (Audit n5 P-01, documented debt), no longer accurate. */
    public static final String FREELANCER_ORG_CREATED = "tnt.freelancer_org.created";
    public static final String FREELANCER_ORG_SUB_DELIVERER_ASSOCIATED = "tnt.freelancer_org.sub_deliverer.associated";
    public static final String FREELANCER_ORG_SUB_DELIVERER_REVOKED = "tnt.freelancer_org.sub_deliverer.revoked";
    public static final String FREELANCER_ORG_VERIFIED = "tnt.freelancer_org.verified";
    /** Lot L8 (annex 9.1): literals of organization-core's {@code FreelancerOrgEventPublisherAdapter}; outbound. */
    public static final String FREELANCER_ORG_SUSPENDED = "tnt.freelancer_org.suspended";
    public static final String FREELANCER_ORG_KYC_UPGRADED = "tnt.freelancer_org.kyc.upgraded";
    /** Lot L8 (annex 9.1): literals of actor-core's {@code KafkaActorEventPublisher}; outbound. */
    public static final String ACTOR_FREELANCER_ASSOCIATED = "tnt.actor.freelancer.associated";
    public static final String ACTOR_FREELANCER_DISSOCIATED = "tnt.actor.freelancer.dissociated";
    public static final String ACTOR_FREELANCER_ORG_LINKED = "tnt.actor.freelancer.org.linked";
    public static final String ACTOR_FREELANCER_ORG_UNLINKED = "tnt.actor.freelancer.org.unlinked";

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
    /** Lot L8 (annex 9.1): literals of {@code ResourceKafkaEventPublisher} / {@code ResourceCoreProperties}; outbound. */
    public static final String RESOURCE_VEHICLE_REGISTERED = "tnt.resource.vehicle.registered";
    public static final String RESOURCE_VEHICLE_ASSIGNED = "tnt.resource.vehicle.assigned";
    public static final String RESOURCE_VEHICLE_UNASSIGNED = "tnt.resource.vehicle.unassigned";
    public static final String RESOURCE_VEHICLE_MAINTENANCE = "tnt.resource.vehicle.maintenance";
    public static final String RESOURCE_VEHICLE_RETIRED = "tnt.resource.vehicle.retired";
    public static final String RESOURCE_VEHICLE_LOCATION = "tnt.resource.vehicle.location";
    public static final String RESOURCE_MAINTENANCE_ALERT = "tnt.resource.maintenance.alert";
    public static final String RESOURCE_EQUIPMENT_ASSIGNED = "tnt.resource.equipment.assigned";

    // ── Product / Inventory / Sales (tnt-product-core / tnt-inventory-core / tnt-sales-core, L4) ──

    public static final String PRODUCT_CREATED = "tnt.product.created";
    public static final String PRODUCT_OFFER_PUBLISHED = "tnt.product.offer.published";
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

    // ── Billing wallet (tnt-billing-wallet, L5) ──────────────────────────────────

    /** Consumed by tnt-billing-invoice, tnt-market-back-core and agency-commission (tnt-accounting-core stopped
     *  listening in lot L6) (Audit n5 P-01 fix — was tnt.billing.wallet.payment-confirmed / tnt.billing.payment.confirmed). */
    public static final String BILLING_WALLET_PAYMENT_CONFIRMED = "tnt.billing.wallet.payment.confirmed";
    /** Consumed by tnt-billing-wallet itself (intra-module) and tnt-accounting-core
     *  (Audit n5 P-01 fix — was tnt.billing.wallet.commission-calculated / tnt.billing.commission.calculated). */
    public static final String BILLING_WALLET_COMMISSION_CALCULATED = "tnt.billing.wallet.commission.calculated";

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

    // ── Link (tnt-link-back-core, coreBackend) ───────────────────────────────────

    /** Compacted (cleanup.policy=compact), keyed by nodeId — last-known-position snapshot for
     *  the Link live map (Chantier G, Audit n5 P-17 / Audit n6 S26). First compacted topic in
     *  this codebase. Named with the {@code tnt.link.*} module prefix (Audit n5 P-12 convention)
     *  rather than the audit doc's original {@code tnt.tracking.position.latest} suggestion,
     *  which had no module prefix. Consumed by tnt-link-back-core's own materialization path
     *  (read model, not this topic itself — see {@code LinkPositionRedisRepository}); published
     *  by {@code NetworkNodeApplicationService.updateLocation} via the shared outbox. */
    public static final String LINK_TRACKING_POSITION_LATEST = "tnt.link.tracking.position.latest";

    // ── Go-Freelancer-Point (tnt-go-freelancer-point-back-core, coreBackend) ────
    // Deliberately out of the "tnt.*" convention — flagged in Audit n5 P-12, kept as-is pending a decision on
    // whether GOFP should adopt the tnt.* prefix. Lot L8: gofp.announcement.published (no producer nor reader since
    // lot L2) is removed, the announcements being on tnt.announcement.events.

    /** Freelancer subscription attempt on a published announcement (async confirmation pipeline). */
    public static final String GOFP_SUBSCRIPTION_ATTEMPTS = "gofp.subscription.attempts";
    /** Push/SSE matching notifications to freelancers after TOPSIS ranking. */
    public static final String GOFP_MATCHING_NOTIFICATIONS = "gofp.matching.notifications";
    /** Ranked freelancer candidates for an announcement (TOPSIS output). */
    public static final String GOFP_ANNOUNCEMENT_CANDIDATES_RANKED = "gofp.announcement.candidates.ranked";
    /** Freelancer account created in GOFP. */
    public static final String GOFP_FREELANCER_CREATED = "gofp.freelancer.created";

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

    /** Published by tnt-roles-core's {@code PlatformAdminAuditEventPublisher} on every grant or revocation of a
     *  platform role (TNT_ADMIN), decision D-172. Audit trail for the administration platform; the durable record
     *  stays the registry table {@code tnt_platform_role_grants}. Key: the account id. */
    public static final String ROLES_PLATFORM_ADMIN_CHANGED = "tnt.roles.platform-admin-changed";

    // ── Trust (tnt-trust-core, L6) — external Kernel contract, not renamed by this referential ──

    public static final String TRUST_EVENTS = "yow.trust.events";
    public static final String TRUST_EVENTS_COMMITTED = "yow.trust.events.committed";

    // ── Cross-cutting infrastructure ─────────────────────────────────────────────

    /** Common dead-letter topic of the Kafka consumers (decision [G.8], lot L0): a record whose
     *  listener still fails after {@code tnt.kafka.retry.max-attempts} attempts is published here by
     *  the {@code DeadLetterPublishingRecoverer} that {@code TntKafkaErrorHandlingPostProcessor}
     *  (yow-event-kernel) installs on every listener container factory, with the original headers
     *  and the {@code kafka_dlt-*} headers (original topic/partition/offset/group, exception). */
    public static final String DLQ = "tnt.dlq";
}
