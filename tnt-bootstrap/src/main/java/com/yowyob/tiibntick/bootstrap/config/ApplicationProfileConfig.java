package com.yowyob.tiibntick.bootstrap.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Produces the {@link ApplicationProfile} and {@link SolutionContext} beans
 * from Spring {@code @Value} bindings.
 * <p>
 * These beans are consumed by:
 * <ul>
 *   <li>{@link TiiBnTickApplicationContext} — runtime state</li>
 *   <li>{@link com.yowyob.tiibntick.bootstrap.bridge.YowyobKernelBridge} — kernel connectivity</li>
 *   <li>{@link com.yowyob.tiibntick.bootstrap.actuator.TntInfoContributor} — /actuator/info</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class ApplicationProfileConfig {

    @Value("${spring.profiles.active:dev}")
    private String activeProfileStr;

    @Value("${tnt.solution.code:TNT}")
    private String solutionCode;

    @Value("${tnt.kernel.base-url:TNT_KERNEL_BASE_URL}")
    private String kernelBaseUrl;

    @Value("${tnt.kernel.api-key:changeme-kernel-api-key}")
    private String kernelApiKey;

    @Value("${spring.application.version:0.0.1}")
    private String version;

    @Bean
    public ApplicationProfile applicationProfile() {
        ApplicationProfile profile;
        try {
            profile = ApplicationProfile.valueOf(activeProfileStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown Spring profile '{}', defaulting to DEV", activeProfileStr);
            profile = ApplicationProfile.DEV;
        }
        log.info("TiiBnTick Core — Active profile: {}", profile);
        return profile;
    }

    @Bean
    public SolutionContext solutionContext(ApplicationProfile profile) {
        SolutionContext ctx = SolutionContext.builder()
                .solutionCode(solutionCode)
                .solutionName(SolutionContext.TNT_NAME)
                .version(version)
                .buildTimestamp(LocalDateTime.now())
                .activeProfile(profile)
                .kernelApiBaseUrl(kernelBaseUrl)
                .supportedLocales(List.of("fr_CM", "en_CM", "pcm_NG", "fr_SN", "en_NG", "en_KE"))
                .supportedCurrencies(List.of("XAF", "XOF", "NGN", "KES", "GHS", "USD", "EUR"))
                .build();

        log.info("TiiBnTick SolutionContext initialized — code={}, version={}, kernel={}",
                ctx.getSolutionCode(), ctx.getVersion(), kernelBaseUrl);
        return ctx;
    }
}
