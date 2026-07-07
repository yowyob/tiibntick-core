package com.yowyob.kernel.i18n.domain.vo;

import java.util.Map;

/**
 * Value Object representing a localized message with its interpolation capability.
 * Supports {{variable}} template syntax used across all TiiBnTick notification templates.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public record LocalizedMessage(String key, String language, String content) {

    /**
     * Interpolates template variables using {{key}} syntax.
     * Example: "Package {{id}} delivered" with params {id: "PKG-42"} -> "Package PKG-42 delivered"
     *
     * @param parameters map of variable names to their values
     * @return the message with all variables replaced
     */
    public String interpolate(Map<String, Object> parameters) {
        if (content == null || parameters == null || parameters.isEmpty()) {
            return content;
        }
        String result = content;
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String val = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace("{{" + entry.getKey() + "}}", val);
        }
        return result;
    }

    /**
     * Returns true if this message contains unreplaced template variables.
     */
    public boolean hasUnresolvedVariables() {
        return content != null && content.contains("{{");
    }
}
