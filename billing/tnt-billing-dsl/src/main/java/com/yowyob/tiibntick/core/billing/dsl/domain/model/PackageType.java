package com.yowyob.tiibntick.core.billing.dsl.domain.model;

/**
 * Classification of parcel types used as a DSL variable in pricing rules.
 * Each type may trigger specific surcharges in the billing engine.
 *
 * @author MANFOUO Braun
 */
public enum PackageType {

    STANDARD,
    FRAGILE,
    PERISHABLE,
    OVERSIZED,
    HAZARDOUS,
    DOCUMENT,
    ELECTRONICS,
    LIQUID
}
