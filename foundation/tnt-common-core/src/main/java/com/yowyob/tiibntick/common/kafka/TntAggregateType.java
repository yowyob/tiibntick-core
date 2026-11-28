package com.yowyob.tiibntick.common.kafka;

import java.util.Locale;
import java.util.Objects;

/**
 * The five aggregates that publish their life cycle on a dedicated Kafka topic (decision [G.1] of
 * 2026-11-26, {@code docs/architecture/chantiers/cible-evenements-colis-livraison.md} §3.1).
 *
 * <p>The constant name is the value of the envelope field {@code aggregateType} and of the header
 * {@code X-Yow-Aggregate-Type}; {@link #topic()} is the single topic of the aggregate (key = aggregate id).
 *
 * <p>{@link #eventTypeFor(String)} applies the mechanical event naming rule of spec §1.0 rules 2 and 3:
 * {@code eventType = <Aggregate> + PascalCase(<target state | change kind>)}, the aggregate word not
 * being repeated ({@code DELIVERY_ATTEMPT_FAILED} → {@code DeliveryAttemptFailed},
 * {@code PICKED_UP} → {@code DeliveryPickedUp} / {@code ParcelPickedUp}).
 */
public enum TntAggregateType {

    PARCEL("Parcel", TntTopics.PARCEL_EVENTS),
    DELIVERY("Delivery", TntTopics.DELIVERY_EVENTS),
    INCIDENT("Incident", TntTopics.INCIDENT_EVENTS),
    DISPUTE("Dispute", TntTopics.DISPUTE_EVENTS),
    ANNOUNCEMENT("Announcement", TntTopics.ANNOUNCEMENT_EVENTS);

    private final String eventTypePrefix;
    private final String topic;

    TntAggregateType(final String eventTypePrefix, final String topic) {
        this.eventTypePrefix = eventTypePrefix;
        this.topic = topic;
    }

    /** @return the aggregate's Kafka topic */
    public String topic() {
        return topic;
    }

    /** @return the PascalCase word every event type of this aggregate starts with */
    public String eventTypePrefix() {
        return eventTypePrefix;
    }

    /**
     * Mechanical event type of a transition to {@code stateOrChangeKind} (an enum constant name such as
     * {@code PICKED_UP}, or a change kind such as {@code ETA_UPDATED}).
     *
     * @param stateOrChangeKind UPPER_SNAKE_CASE name of the target state or of the change kind
     * @return {@code <Aggregate><PascalCase>}, the aggregate word not repeated
     * @throws IllegalArgumentException if the name is blank or not UPPER_SNAKE_CASE
     */
    public String eventTypeFor(final String stateOrChangeKind) {
        Objects.requireNonNull(stateOrChangeKind, "stateOrChangeKind");
        if (!stateOrChangeKind.matches("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*")) {
            throw new IllegalArgumentException("Not an UPPER_SNAKE_CASE state or change kind: " + stateOrChangeKind);
        }
        String pascal = pascalCase(stateOrChangeKind);
        boolean alreadyPrefixed = pascal.startsWith(eventTypePrefix)
            && (pascal.length() == eventTypePrefix.length()
                || Character.isUpperCase(pascal.charAt(eventTypePrefix.length())));
        return alreadyPrefixed && pascal.length() > eventTypePrefix.length() ? pascal : eventTypePrefix + pascal;
    }

    /** {@code DELIVERY_ATTEMPT_FAILED} → {@code DeliveryAttemptFailed}. */
    static String pascalCase(final String upperSnake) {
        StringBuilder out = new StringBuilder(upperSnake.length());
        for (String word : upperSnake.split("_")) {
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
