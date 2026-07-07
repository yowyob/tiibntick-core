package com.yowyob.tiibntick.core.billing.dsl.domain.model;

/**
 * Types of actions that a DSL rule can execute on the running price.
 *
 * <ul>
 *   <li>{@code SET_BASE}   — replaces the base price with a fixed amount</li>
 *   <li>{@code ADD_FIXED}  — adds a fixed surcharge (e.g. +500 XAF for FRAGILE)</li>
 *   <li>{@code ADD_PCT}    — adds a percentage surcharge (e.g. +15% for HIGH priority)</li>
 *   <li>{@code DISCOUNT_PCT} — applies a percentage discount (e.g. -5% loyalty)</li>
 *   <li>{@code DISCOUNT_FIXED} — applies a fixed discount</li>
 *   <li>{@code SET_PER_KM} — sets the per-kilometre rate</li>
 *   <li>{@code SET_PER_KG} — sets the per-kilogram rate</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum DslActionType {
    SET_BASE,
    ADD_FIXED,
    ADD_PCT,
    DISCOUNT_PCT,
    DISCOUNT_FIXED,
    SET_PER_KM,
    SET_PER_KG
}
