package com.yowyob.tiibntick.core.tp.application.port.in.command;

import java.util.UUID;

/**
 * Command to generate and assign a phone alias for a third party (relay-point anonymity).
 *
 * @author MANFOUO Braun
 */
public record GeneratePhoneAliasCommand(
        UUID tenantId,
        UUID thirdPartyId
) {}
