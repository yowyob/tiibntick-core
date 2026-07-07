package com.yowyob.tiibntick.core.geo.domain.model;

/**
 * Semantic classification of road network nodes within the TiiBnTick logistics graph.
 *
 * Author: MANFOUO Braun
 */
public enum NodeType {

    /** Client pickup or delivery address. */
    CLIENT_POINT,

    /** Agency-managed parcel relay hub (physical sorting centre). */
    RELAY_HUB,

    /** Warehouse / main depot where missions originate. */
    DEPOT,

    /** Intermediate routing waypoint with no parcel-handling capability. */
    WAYPOINT
}
