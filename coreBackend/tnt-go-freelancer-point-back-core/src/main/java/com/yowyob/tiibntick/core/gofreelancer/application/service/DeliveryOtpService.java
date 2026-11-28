package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service responsible for initialising OTP codes on a {@link Delivery}.
 *
 * <p>The Core does not send these codes by e-mail or push: the owner of the delivery need reads
 * them in the app ({@link #readHandoverCodes}), and account OTP/MFA belongs to the Kernel.
 *
 * <p>Two codes are generated per delivery:
 * <ul>
 *   <li><b>Pickup OTP</b> — for the shipper (expéditeur). The delivery person
 *       must collect this code from the shipper and submit it when calling
 *       {@code PATCH /deliveries/{id}/status} with {@code status=PICKED_UP}.</li>
 *   <li><b>Delivery OTP</b> — for the recipient (destinataire). The delivery
 *       person must collect this code from the recipient and submit it when calling
 *       {@code PATCH /deliveries/{id}/status} with {@code status=DELIVERED} (direct, no relay point).</li>
 * </ul>
 *
 * <p>The BCrypt hash is the reference for verification. Since lot C-53 each code is also kept
 * encrypted ({@link HandoverCodeCipher}, AES-256-GCM) so that the owner of the delivery need can
 * read it again in the app ({@link #readHandoverCodes}); the plain code is never stored nor logged.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOtpService {

    private final OtpService otpService;
    private final DeliveryRepository deliveryRepository;
    private final HandoverCodeCipher cipher;

    /** The plain codes still useful for a delivery; {@code null} for a code that is no longer needed. */
    public record HandoverCodes(String pickupCode, String deliveryCode) { }

    /**
     * Initialises OTP codes for the given delivery if they have not been set yet (lazy-init).
     *
     * @param delivery the delivery to initialise
     * @return the updated delivery (saved in DB) with hashes populated
     */
    public Mono<Delivery> initOtpIfAbsent(Delivery delivery) {
        return initOtpIfAbsentWithCodes(delivery).map(OtpInitResult::delivery);
    }

    /**
     * Same as {@link #initOtpIfAbsent} but exposes newly generated plain OTPs once
     * (for assign response to the shipper FE).
     */
    public Mono<OtpInitResult> initOtpIfAbsentWithCodes(Delivery delivery) {
        if (delivery.getPickupOtpHash() != null && delivery.getDeliveryOtpHash() != null) {
            log.debug("OTP codes already initialised for delivery {}", delivery.getId());
            return Mono.just(new OtpInitResult(delivery, null, null, false));
        }

        log.info("Initialising OTP codes for delivery {}", delivery.getId());

        String pickupOtp = otpService.generateOtp();
        String deliveryOtp = otpService.generateOtp();

        delivery.setPickupOtpHash(otpService.hashOtp(pickupOtp));
        delivery.setDeliveryOtpHash(otpService.hashOtp(deliveryOtp));
        delivery.setPickupCodeEnc(cipher.encrypt(pickupOtp));
        delivery.setDeliveryCodeEnc(cipher.encrypt(deliveryOtp));

        return deliveryRepository.save(delivery)
                .map(saved -> new OtpInitResult(saved, pickupOtp, deliveryOtp, true));
    }

    /**
     * Lot C-53 — the plain codes the owner of the need still needs: the pickup code when
     * {@code pickupUseful}, the delivery code when {@code deliveryUseful}. A code that is useful but
     * cannot be read back (delivery created before C-53: hash only; or the key changed) is
     * regenerated — new hash and new encrypted value, saved — so the owner always gets a code that
     * the verification accepts. Codes are created first when the delivery has none yet.
     */
    public Mono<HandoverCodes> readHandoverCodes(Delivery delivery, boolean pickupUseful, boolean deliveryUseful) {
        return initOtpIfAbsent(delivery).flatMap(d -> {
            boolean changed = false;
            String pickup = null;
            String deliveryCode = null;
            if (pickupUseful) {
                pickup = cipher.decrypt(d.getPickupCodeEnc());
                if (pickup == null) {
                    pickup = otpService.generateOtp();
                    d.setPickupOtpHash(otpService.hashOtp(pickup));
                    d.setPickupCodeEnc(cipher.encrypt(pickup));
                    changed = true;
                }
            }
            if (deliveryUseful) {
                deliveryCode = cipher.decrypt(d.getDeliveryCodeEnc());
                if (deliveryCode == null) {
                    deliveryCode = otpService.generateOtp();
                    d.setDeliveryOtpHash(otpService.hashOtp(deliveryCode));
                    d.setDeliveryCodeEnc(cipher.encrypt(deliveryCode));
                    changed = true;
                }
            }
            HandoverCodes codes = new HandoverCodes(pickup, deliveryCode);
            if (!changed) {
                return Mono.just(codes);
            }
            log.info("Handover codes re-issued for delivery {} (no readable copy)", d.getId());
            return deliveryRepository.save(d).thenReturn(codes);
        });
    }
}
