package com.yowyob.tiibntick.core.billing.dsl.infrastructure.dsl.lexer;

/**
 * Exhaustive list of token types produced by the {@link DslLexer}.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #CONTAINS}        — for {@code activeEquipmentTypes CONTAINS REFRIGERATED_BOX}</li>
 *   <li>{@link #DAY_IS}          — for {@code dayOfWeek DAY_IS WEEKEND|WEEKDAY|SATURDAY…}</li>
 *   <li>{@link #TIME_IS_BETWEEN} — for {@code timeOfDay TIME_IS_BETWEEN 22:00 AND 06:00}</li>
 *   <li>{@link #TIME_LITERAL}    — for HH:MM time literals (e.g. {@code 22:00}, {@code 06:30})</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
public enum TokenType {

    // Identifiers and literals
    IDENTIFIER,     // weight, distance, packageType …
    NUMBER,         // 5, 3.2, 1000.0
    STRING,         // "FRAGILE", 'HIGH'
    TIME_LITERAL,   // 22:00, 06:30 — HH:MM format ()

    // Keywords
    AND,
    OR,
    NOT,
    IN,
    BETWEEN,
    TRUE,
    FALSE,

    //  — New operator keywords
    /** Tests list membership: {@code activeEquipmentTypes CONTAINS REFRIGERATED_BOX} */
    CONTAINS,
    /** Tests day classification: {@code dayOfWeek DAY_IS WEEKEND} */
    DAY_IS,
    /** Tests time range: {@code timeOfDay TIME_IS_BETWEEN 22:00 AND 06:00} */
    TIME_IS_BETWEEN,

    // Comparison operators
    EQ,             // ==
    NEQ,            // !=
    LT,             // <
    LTE,            // <=
    GT,             // >
    GTE,            // >=

    // Delimiters
    LPAREN,         // (
    RPAREN,         // )
    LBRACKET,       // [
    RBRACKET,       // ]
    COMMA,          // ,

    // Action keywords
    ACTION,         // ACTION keyword introducing an action clause
    SET_BASE,
    ADD_FIXED,
    ADD_PCT,
    DISCOUNT_PCT,
    DISCOUNT_FIXED,
    SET_PER_KM,
    SET_PER_KG,

    // Special
    EOF
}
