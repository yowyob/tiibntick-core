package com.yowyob.tiibntick.core.roles.domain.model;

/**
 * How a platform role was obtained (decisions D-159, D-161).
 *
 * @author MANFOUO Braun
 */
public enum PlatformGrantKind {
    /** The Kernel owner of TiiBnTick's system tenant, recorded at their first login in that tenant (or on claim). */
    SYSTEM_OWNER,
    /** Granted by an active TNT_ADMIN. */
    ASSIGNED
}
