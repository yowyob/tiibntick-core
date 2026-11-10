package com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors the Kernel's {@code PaymentChallengeResponse}: the first step of every confirmed
 * wallet operation (transfer, withdrawal, pay). The Kernel sends a one-time code to the user
 * out of band; the caller then repeats the operation with {@code challengeToken} + {@code code}.
 *
 * <p>{@code codePreview} is a masked hint of where the code was sent (never the code itself).
 *
 * @author MANFOUO Braun
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelChallengeDto(
        String challengeToken,
        Long expiresInSeconds,
        String codePreview
) {
}
