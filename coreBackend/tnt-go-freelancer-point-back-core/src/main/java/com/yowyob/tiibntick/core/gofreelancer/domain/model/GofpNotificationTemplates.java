package com.yowyob.tiibntick.core.gofreelancer.domain.model;

/**
 * i18n template keys for Go-Freelancer-Point notifications routed via tnt-notify-core.
 */
public final class GofpNotificationTemplates {

    private GofpNotificationTemplates() {
    }

    public static final String MESSAGE = "notify.gofp.message";
    public static final String REGISTRATION_RECEIVED = "notify.gofp.registration_received";
    public static final String ACCOUNT_APPROVED = "notify.gofp.account_approved";
    public static final String ACCOUNT_REJECTED = "notify.gofp.account_rejected";
    public static final String ACCOUNT_SUSPENDED = "notify.gofp.account_suspended";
    public static final String ACCOUNT_REVOKED = "notify.gofp.account_revoked";
    public static final String DELIVERY_ASSIGNED = "notify.gofp.delivery_assigned";
    public static final String NEW_ANNOUNCEMENT = "notify.gofp.new_announcement";
    public static final String EVALUATION_REQUEST = "notify.gofp.evaluation_request";

    // ── Messages written once per language, with parameters (catalog seed i18n-005) ──
    public static final String DELIVERY_PICKED_UP = "notify.gofp.delivery.picked_up";
    public static final String DELIVERY_IN_TRANSIT = "notify.gofp.delivery.in_transit";
    public static final String DELIVERY_AT_RELAY_HUB = "notify.gofp.delivery.at_relay_hub";
    public static final String DELIVERY_DELIVERED = "notify.gofp.delivery.delivered";
    public static final String DELIVERY_FAILED = "notify.gofp.delivery.failed";
    public static final String DELIVERY_CANCELLED = "notify.gofp.delivery.cancelled";
    public static final String DELIVERY_PAUSED = "notify.gofp.delivery.paused";
    public static final String DELIVERY_LATE = "notify.gofp.delivery.late";
    public static final String RELAY_PARCEL_DEPOSITED = "notify.gofp.relay.parcel_deposited";
    public static final String RELAY_PARCEL_ARRIVED = "notify.gofp.relay.parcel_arrived";
    public static final String RELAY_DELIVERY_EXPECTED = "notify.gofp.relay.delivery_expected";
    public static final String RELAY_STATUS_APPROVED = "notify.gofp.relay_status.approved";
    public static final String RELAY_STATUS_REJECTED = "notify.gofp.relay_status.rejected";
    public static final String RELAY_STATUS_REJECTED_REASON = "notify.gofp.relay_status.rejected_reason";
    public static final String RELAY_STATUS_SUSPENDED = "notify.gofp.relay_status.suspended";
    public static final String RELAY_STATUS_REVOKED = "notify.gofp.relay_status.revoked";
    /** Revocation with the administrator's reason (D-129), parameter {@code reason}. */
    public static final String RELAY_STATUS_REVOKED_REASON = "notify.gofp.relay_status.revoked_reason";
    public static final String RELAY_STATUS_REACTIVATED = "notify.gofp.relay_status.reactivated";
    public static final String CHAT_NEW_MESSAGE = "notify.gofp.chat.new_message";
    public static final String CHAT_NEW_MESSAGE_FROM = "notify.gofp.chat.new_message_from";
    public static final String ROUTE_UPDATED = "notify.gofp.route.updated";
    public static final String ROUTE_IMPROVED = "notify.gofp.route.improved";
    public static final String ANNOUNCEMENT_NEW_CANDIDATES = "notify.gofp.announcement.new_candidates";
    public static final String ANNOUNCEMENT_NEW_CANDIDATES_FILTERED = "notify.gofp.announcement.new_candidates_filtered";
    public static final String ANNOUNCEMENT_AVAILABLE = "notify.gofp.announcement.available";
    public static final String EVALUATION_CLIENT_PICKUP = "notify.gofp.evaluation.client_pickup";
    public static final String EVALUATION_RELAY_HANDOVER = "notify.gofp.evaluation.relay_handover";
    public static final String PASSWORD_RESET = "notify.gofp.password_reset";
}
