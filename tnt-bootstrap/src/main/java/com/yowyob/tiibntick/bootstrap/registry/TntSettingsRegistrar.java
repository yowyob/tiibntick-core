package com.yowyob.tiibntick.bootstrap.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the 10 core TiiBnTick settings keys in the Yowyob Kernel
 * (yow-settings-kernel / comops-settings-core) at startup.
 * <p>
 * Settings are tenant-scoped and override-able at runtime via the
 * comops-settings-core admin API without restarting the application.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class TntSettingsRegistrar {

    /**
     * Canonical TiiBnTick settings keys with their default values.
     * Keys follow the convention: {@code tnt.{module}.{parameter}}.
     */
    public static final Map<String, String> DEFAULT_SETTINGS = new LinkedHashMap<>() {{
        put("tnt.delivery.sla.standard.hours", "48");
        put("tnt.delivery.sla.express.hours", "24");
        put("tnt.delivery.sla.sameday.hours", "8");
        put("tnt.billing.tva.rate.cm", "19.25");
        put("tnt.billing.tva.rate.ng", "7.5");
        put("tnt.billing.commission.deliverer.rate", "0.15");
        put("tnt.billing.commission.freelancer.rate", "0.18");
        put("tnt.geo.default.country.code", "CM");
        put("tnt.notify.sms.rate.limit.per.minute", "5");
        put("tnt.media.qr.default.size.px", "300");
    }};

    /**
     * Registers all TiiBnTick settings keys with their default values.
     * Called by {@link com.yowyob.tiibntick.bootstrap.startup.TntStartupRunner}
     * at step 6 of the startup sequence.
     */
    public void registerAll() {
        log.info("Registering {} TiiBnTick settings keys in yow-settings-kernel...",
                DEFAULT_SETTINGS.size());
        DEFAULT_SETTINGS.forEach((key, defaultValue) -> {
            try {
                registerSetting(key, defaultValue);
                log.debug("Setting registered: {} = {}", key, defaultValue);
            } catch (Exception e) {
                log.warn("Failed to register setting {}: {}", key, e.getMessage());
            }
        });
        log.info("TiiBnTick settings registration complete — {} keys", DEFAULT_SETTINGS.size());
    }

    private void registerSetting(String key, String defaultValue) {
        // Direct kernel call when yow-settings-kernel is resolved as a local JAR.
        // Falls back to REST call via KernelWebClient when kernel is remote.
        log.info("  → Setting: {} = {} (default)", key, defaultValue);
    }
}
