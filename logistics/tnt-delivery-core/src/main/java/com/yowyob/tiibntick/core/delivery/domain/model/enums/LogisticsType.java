package com.yowyob.tiibntick.core.delivery.domain.model.enums;

/**
 * Type of logistics vehicle used by a delivery person.
 * Influences capacity constraints and cost computation in the VRP model.
 *
 * @author MANFOUO Braun
 */
public enum LogisticsType {

    BIKE,
    MOTORBIKE,
    CAR,
    VAN,
    TRUCK
}
