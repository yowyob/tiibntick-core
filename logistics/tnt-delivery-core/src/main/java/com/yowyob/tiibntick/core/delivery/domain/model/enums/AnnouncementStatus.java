package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Lifecycle status of a client delivery announcement (TiiBnTick-style announcements).
 *
 * <p>State flow:
 * <pre>
 *   DRAFT ──► PUBLISHED ──► IN_NEGOTIATION ──► ASSIGNED ──► COMPLETED
 *                 │                │                 │
 *                 └──► CANCELLED   └──► CANCELLED    └──► CANCELLED
 * </pre>
 *
 * @author MANFOUO Braun
 */
public enum AnnouncementStatus {

    /** Announcement saved but not yet visible to delivery persons. */
    DRAFT,

    /** Announcement published and visible to all eligible delivery persons. */
    PUBLISHED,

    /** At least one delivery person has responded; client is reviewing offers. */
    IN_NEGOTIATION,

    /** Client has selected a delivery person; delivery has been created. */
    ASSIGNED,

    /** Associated delivery completed successfully. */
    COMPLETED,

    /** Announcement cancelled by the client before assignment. */
    CANCELLED
}
