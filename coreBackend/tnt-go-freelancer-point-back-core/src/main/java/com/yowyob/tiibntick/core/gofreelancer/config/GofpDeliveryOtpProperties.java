package com.yowyob.tiibntick.core.gofreelancer.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for GOFP delivery OTP behaviour.
 *
 * <p>{@code preview-mode}: when {@code true}, {@code buildAssignResponse} includes
 * {@code deliveryConfirmationCode} (the plain-text delivery OTP) in the HTTP response —
 * reproducing the kernel's {@code PREVIEW_ONLY} mechanism used for the connexion OTP.
 * Must be {@code false} in every production environment.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Getter
@Setter
@ConfigurationProperties("tnt.gofp.delivery-otp")
public class GofpDeliveryOtpProperties {

    private boolean previewMode = false;

    @PostConstruct
    void warnIfPreview() {
        if (previewMode) {
            log.warn("╔══════════════════════════════════════════════════════════════════╗");
            log.warn("║  deliveryOtp PREVIEW MODE actif — NE JAMAIS activer en production ║");
            log.warn("║  deliveryConfirmationCode est renvoyé en clair dans les réponses  ║");
            log.warn("║  HTTP d'assignation. Désactiver avant tout déploiement.            ║");
            log.warn("╚══════════════════════════════════════════════════════════════════╝");
        }
    }
}
