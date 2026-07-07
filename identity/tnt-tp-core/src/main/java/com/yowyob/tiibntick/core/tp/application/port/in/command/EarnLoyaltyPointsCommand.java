package com.yowyob.tiibntick.core.tp.application.port.in.command;

import java.util.UUID;

/**
 * Command to credit loyalty points to a third party's account.
 *
 * @author MANFOUO Braun
 */
public record EarnLoyaltyPointsCommand(
        UUID tenantId,
        UUID thirdPartyId,
        int points,
        String missionId
) {}
