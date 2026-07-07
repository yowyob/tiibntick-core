package com.yowyob.tiibntick.core.billing.dsl.domain.model;

/**
 * Road surface type used as a DSL variable.
 * Reflects African-specific road conditions as described in the mathematical
 * model: u_terrain(a) factor increases operational cost on degraded roads.
 *
 * @author MANFOUO Braun
 */
public enum RoadType {

    PAVED,
    UNPAVED,
    MUD,
    HIGHWAY,
    URBAN_STREET,
    ALLEY
}
