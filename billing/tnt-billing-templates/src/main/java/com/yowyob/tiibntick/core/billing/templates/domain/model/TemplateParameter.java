package com.yowyob.tiibntick.core.billing.templates.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Immutable value object representing a single adjustable parameter in a billing
 * policy template.
 *
 * <p>Each parameter has a typed default value and optional min/max constraints.
 * The UI uses these constraints to render the appropriate input widget (slider,
 * numeric input, toggle) and to validate user input before applying the template.
 *
 * <p>Template parameters are identified by their {@code key} within the scope of
 * a given template (key uniqueness is enforced at the template level).
 *
 * <p><b>Examples:</b>
 * <pre>
 *   key=basePrice, type=MONEY, defaultValue=500, minValue=100, maxValue=50000, unit=XAF
 *   key=fragile_surcharge_pct, type=PERCENTAGE, defaultValue=20, minValue=0, maxValue=100, unit=%
 *   key=night_multiplier, type=MULTIPLIER, defaultValue=1.5, minValue=1.0, maxValue=3.0, unit=x
 * </pre>
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Value
@Builder
public class TemplateParameter {

    /**
     * Technical key used to reference this parameter in DSL rule generation.
     * Must be unique within the parent template. Example: {@code basePrice}.
     */
    String key;

    /**
     * Human-readable label in French. Displayed in the TiiBnTick UI.
     */
    String labelFr;

    /**
     * Human-readable label in English. Used in API responses and i18n.
     */
    String labelEn;

    /**
     * The default value pre-filled when the actor applies the template.
     * Stored as a string to support all parameter types uniformly.
     * Must be parseable according to {@link #type}.
     */
    String defaultValue;

    /**
     * Minimum acceptable value for this parameter (nullable = no lower bound).
     * Enforced by {@code TemplateParameterValidationService}.
     */
    String minValue;

    /**
     * Maximum acceptable value for this parameter (nullable = no upper bound).
     * Enforced by {@code TemplateParameterValidationService}.
     */
    String maxValue;

    /**
     * Display unit shown alongside the input field.
     * Examples: {@code XAF}, {@code %}, {@code km}, {@code kg}, {@code h}, {@code x}.
     */
    String unit;

    /**
     * The semantic type of this parameter, determining parsing and UI widget.
     */
    ParameterType type;

    /**
     * Contextual help text explaining the impact of this parameter.
     * Displayed as a tooltip or info icon in the UI.
     */
    String helpText;

    // ─── Parsed convenience accessors ──────────────────────────────────────

    /**
     * Returns the default value parsed as a {@link BigDecimal}.
     * Valid for MONEY, PERCENTAGE, DECIMAL, MULTIPLIER types.
     *
     * @return parsed BigDecimal default value
     * @throws NumberFormatException if the default value is not numeric
     */
    public BigDecimal getDefaultValueAsBigDecimal() {
        return new BigDecimal(defaultValue);
    }

    /**
     * Returns the default value parsed as an integer.
     * Valid for INTEGER type.
     *
     * @return parsed integer default value
     * @throws NumberFormatException if the default value is not an integer
     */
    public int getDefaultValueAsInt() {
        return Integer.parseInt(defaultValue);
    }

    /**
     * Returns the default value parsed as a boolean.
     * Valid for BOOLEAN type.
     *
     * @return parsed boolean default value
     */
    public boolean getDefaultValueAsBoolean() {
        return Boolean.parseBoolean(defaultValue);
    }

    /**
     * Returns the minimum value parsed as a {@link BigDecimal}, or null if no minimum is set.
     *
     * @return parsed BigDecimal minimum, or null
     */
    public BigDecimal getMinValueAsBigDecimal() {
        return minValue != null ? new BigDecimal(minValue) : null;
    }

    /**
     * Returns the maximum value parsed as a {@link BigDecimal}, or null if no maximum is set.
     *
     * @return parsed BigDecimal maximum, or null
     */
    public BigDecimal getMaxValueAsBigDecimal() {
        return maxValue != null ? new BigDecimal(maxValue) : null;
    }
}
