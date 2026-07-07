package com.yowyob.tiibntick.core.tp.application.port.in.command;

import java.util.UUID;

/**
 * Command to redeem loyalty points.
 *
 * @author MANFOUO Braun
 */
public record RedeemLoyaltyPointsCommand(
        UUID tenantId,
        UUID thirdPartyId,
        int points,
        String invoiceId
) {}
