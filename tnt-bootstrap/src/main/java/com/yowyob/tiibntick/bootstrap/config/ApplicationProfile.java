package com.yowyob.tiibntick.bootstrap.config;

/**
 * Represents the active Spring Boot profile for TiiBnTick Core.
 * Drives security rules, Swagger visibility, log levels, and tracing sampling.
 *
 * @author MANFOUO Braun
 */
public enum ApplicationProfile {
    DEV,
    TEST,
    STAGING,
    PROD;

    public boolean isProduction() {
        return this == PROD;
    }

    public boolean isDevelopment() {
        return this == DEV || this == TEST;
    }
}
