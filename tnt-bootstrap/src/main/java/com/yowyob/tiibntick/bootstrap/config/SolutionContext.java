package com.yowyob.tiibntick.bootstrap.config;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Value object representing the runtime context of the TiiBnTick solution.
 * Injected into the Spring context by {@link ApplicationProfileConfig} and consumed
 * by the Swagger info, actuator/info endpoint, and the kernel bridge.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class SolutionContext {

    public static final String TNT_CODE = "TNT";
    public static final String TNT_NAME = "TiiBnTick";

    private final String solutionCode;
    private final String solutionName;
    private final String version;
    private final LocalDateTime buildTimestamp;
    private final ApplicationProfile activeProfile;
    private final String kernelApiBaseUrl;

    /** BCP 47 locales supported by tnt-i18n-kernel. */
    private final List<String> supportedLocales;

    /** ISO 4217 currencies supported (Africa-first). */
    private final List<String> supportedCurrencies;

    public boolean isProduction() {
        return activeProfile != null && activeProfile.isProduction();
    }

    public boolean isDevelopment() {
        return activeProfile == null || activeProfile.isDevelopment();
    }
}
