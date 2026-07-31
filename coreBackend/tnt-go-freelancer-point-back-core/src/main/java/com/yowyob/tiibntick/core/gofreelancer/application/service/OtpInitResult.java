package com.yowyob.tiibntick.core.gofreelancer.application.service;

/**
 * Result of lazy OTP initialisation — plain codes are only present when newly generated.
 *
 * @author MANFOUO BRAUN
 */
public record OtpInitResult(
        com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery delivery,
        String pickupOtp,
        String deliveryOtp,
        boolean newlyInitialized) {
}
