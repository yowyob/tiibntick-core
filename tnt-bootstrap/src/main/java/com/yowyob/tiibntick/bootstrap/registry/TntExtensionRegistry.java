package com.yowyob.tiibntick.bootstrap.registry;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of all TiiBnTick-specific extensions to Yowyob Kernel modules.
 * <p>
 * An extension is a TiiBnTick Core module that extends (specializes) a kernel module:
 * e.g., {@code tnt-actor-core} extends {@code comops-actor-core}.
 * <p>
 * Used by {@code /actuator/tnt/modules} and the startup banner to show which
 * kernel modules have been extended by TiiBnTick.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@Getter
public class TntExtensionRegistry {

    public record ExtensionDescriptor(
            String tntModule,
            String kernelBase,
            String addedCapabilities
    ) {}

    private final List<ExtensionDescriptor> extensions = new ArrayList<>();

    /**
     * Registers all TiiBnTick extensions over Kernel modules.
     * Called by {@link com.yowyob.tiibntick.bootstrap.startup.TntStartupRunner} at step 8.
     */
    public void registerAll() {
        register("tnt-actor-core", "comops-actor-core",
                "DelivererProfile, FreelancerProfile, GPS real-time, reputation score");
        register("tnt-organization-core", "comops-organization-core",
                "ServiceZone, RelayHub, African POI, inter-branch transfers");
        register("tnt-tp-core", "comops-tp-core",
                "Shipper loyalty, mobile KYC, GDPR phone masking, rating system");
        register("tnt-administration-core", "comops-administration-core",
                "TNT RBAC roles, freelancer KYC validation, dispute governance");
        register("tnt-notify-core", "comops-kernel-core",
                "WhatsApp Meta API, in-app WebSocket STOMP, logistics i18n templates");
        register("tnt-media-core", "comops-file-core",
                "ZXing QR codes, JasperReports PDF, delivery proof, digital signature");
        register("tnt-resource-core", "comops-resource-core",
                "Vehicle, Equipment, deliverer assignment, maintenance lifecycle");
        register("tnt-product-core", "comops-product-core",
                "ServiceOffer logistics, LogisticsProfile, Market publication");
        register("tnt-accounting-core", "comops-accounting-core",
                "OHADA journal, multi-country balance, commission reconciliation");

        log.info("TiiBnTick Extension Registry — {} extensions registered", extensions.size());
    }

    public List<ExtensionDescriptor> getAll() {
        return Collections.unmodifiableList(extensions);
    }

    private void register(String tntModule, String kernelBase, String capabilities) {
        extensions.add(new ExtensionDescriptor(tntModule, kernelBase, capabilities));
        log.debug("Extension registered: {} → extends {}", tntModule, kernelBase);
    }
}
