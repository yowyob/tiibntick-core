package com.yowyob.tiibntick.core.realtime.domain.model.enums;

/**
 * Lifecycle states of an actor's presence in the TiiBnTick platform.
 *
 * @author MANFOUO Braun
 */
public enum PresenceStatus {

    /** Actor is connected and available to accept missions. */
    ONLINE_AVAILABLE,

    /** Actor is connected and currently executing a mission. */
    ONLINE_ON_MISSION,

    /** Actor is connected but has been idle for a while (no recent GPS ping). */
    ONLINE_IDLE,

    /** Actor is not connected (session closed or TTL expired in Redis). */
    OFFLINE,

    /** Actor is blocked from participation by an administrator. */
    SUSPENDED
}
