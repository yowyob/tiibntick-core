package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.EmailPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.PushNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service responsible for initialising OTP codes on a {@link Delivery}.
 *
 * <p>Two codes are generated per delivery:
 * <ul>
 *   <li><b>Pickup OTP</b> — sent to the shipper (expéditeur). The delivery person
 *       must collect this code from the shipper and submit it when calling
 *       {@code PATCH /deliveries/{id}/status} with {@code status=PICKED_UP}.</li>
 *   <li><b>Delivery OTP</b> — sent to the recipient (destinataire). The delivery
 *       person must collect this code from the recipient and submit it when calling
 *       {@code PATCH /deliveries/{id}/status} with {@code status=DELIVERED} (direct, no relay point).</li>
 * </ul>
 *
 * <p>Only the BCrypt hash is persisted; the plain-text code is communicated once
 * (via push notification and/or email) and never stored.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOtpService {

    private final OtpService otpService;
    private final DeliveryRepository deliveryRepository;
    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final PushNotificationPort pushNotificationPort;
    private final EmailPort emailPort;

    /**
     * Initialises OTP codes for the given delivery if they have not been set yet (lazy-init).
     *
     * <p>If both {@code pickupOtpHash} and {@code deliveryOtpHash} are already present,
     * this method is a no-op and returns the delivery unchanged.</p>
     *
     * @param delivery the delivery to initialise
     * @return the updated delivery (saved in DB) with hashes populated
     */
    public Mono<Delivery> initOtpIfAbsent(Delivery delivery) {
        if (delivery.getPickupOtpHash() != null && delivery.getDeliveryOtpHash() != null) {
            log.debug("OTP codes already initialised for delivery {}", delivery.getId());
            return Mono.just(delivery);
        }

        log.info("Initialising OTP codes for delivery {}", delivery.getId());

        String pickupOtp   = otpService.generateOtp();
        String deliveryOtp = otpService.generateOtp();

        delivery.setPickupOtpHash(otpService.hashOtp(pickupOtp));
        delivery.setDeliveryOtpHash(otpService.hashOtp(deliveryOtp));

        return deliveryRepository.save(delivery)
                .flatMap(saved -> sendOtpNotifications(saved, pickupOtp, deliveryOtp));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private Mono<Delivery> sendOtpNotifications(Delivery delivery, String pickupOtp, String deliveryOtp) {
        UUID deliveryNeedId = delivery.getDeliveryNeedId();

        if (deliveryNeedId == null) {
            log.warn("Delivery {} has no deliveryNeedId — cannot fetch sender/recipient contacts. " +
                     "OTP hashes saved but notifications not sent.", delivery.getId());
            return Mono.just(delivery);
        }

        return deliveryNeedRepository.findById(deliveryNeedId)
                .flatMap(need -> {
                    // ── Push notifications ──────────────────────────────────────────
                    // Notify the sender (userId owns the delivery need)
                    Mono<Void> notifySender = pushNotificationPort.sendPushNotification(
                            need.getUserId(),
                            "Code de remise au livreur",
                            "Votre code de remise de colis est : " + pickupOtp +
                            " — Communiquez ce code au livreur lorsqu'il vient récupérer votre colis."
                    ).onErrorResume(e -> {
                        log.error("Failed to send pickup OTP push to user {}: {}", need.getUserId(), e.getMessage());
                        return Mono.empty();
                    });

                    // ── Emails ──────────────────────────────────────────────────────
                    // Email to sender if available
                    Mono<Void> emailSender = Mono.empty();
                    if (need.getSenderEmail() != null) {
                        emailSender = emailPort.sendSimpleMessageReactive(
                                need.getSenderEmail(),
                                "TiiBnTick — Code de remise de votre colis",
                                buildPickupOtpEmail(pickupOtp, need.getSenderFirstName())
                        ).onErrorResume(e -> {
                            log.error("Failed to send pickup OTP email to {}: {}", need.getSenderEmail(), e.getMessage());
                            return Mono.empty();
                        });
                    }

                    // Email to recipient if available
                    Mono<Void> emailRecipient = Mono.empty();
                    if (need.getRecipientEmail() != null) {
                        emailRecipient = emailPort.sendSimpleMessageReactive(
                                need.getRecipientEmail(),
                                "TiiBnTick — Code de réception de votre colis",
                                buildDeliveryOtpEmail(deliveryOtp, need.getRecipientFirstName())
                        ).onErrorResume(e -> {
                            log.error("Failed to send delivery OTP email to {}: {}", need.getRecipientEmail(), e.getMessage());
                            return Mono.empty();
                        });
                    }

                    return Mono.when(notifySender, emailSender, emailRecipient)
                               .thenReturn(delivery);
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch DeliveryNeed {} for OTP notifications: {}", deliveryNeedId, e.getMessage());
                    // OTP hashes are already saved — notifications failed but delivery is still usable
                    return Mono.just(delivery);
                });
    }

    private String buildPickupOtpEmail(String otp, String firstName) {
        String greeting = (firstName != null && !firstName.isBlank()) ? "Bonjour " + firstName + "," : "Bonjour,";
        return greeting + "\n\n" +
               "Un livreur a été assigné à votre colis.\n\n" +
               "Votre code de remise est : " + otp + "\n\n" +
               "Communiquez ce code au livreur UNIQUEMENT lorsqu'il se présente pour récupérer votre colis.\n" +
               "Ne le partagez pas avant la collecte.\n\n" +
               "Cordialement,\nL'équipe TiiBnTick";
    }

    private String buildDeliveryOtpEmail(String otp, String firstName) {
        String greeting = (firstName != null && !firstName.isBlank()) ? "Bonjour " + firstName + "," : "Bonjour,";
        return greeting + "\n\n" +
               "Un colis est en cours de livraison pour vous.\n\n" +
               "Votre code de confirmation de réception est : " + otp + "\n\n" +
               "Communiquez ce code au livreur UNIQUEMENT lorsqu'il vous remet le colis.\n" +
               "Ne le partagez pas avant la livraison.\n\n" +
               "Cordialement,\nL'équipe TiiBnTick";
    }
}
