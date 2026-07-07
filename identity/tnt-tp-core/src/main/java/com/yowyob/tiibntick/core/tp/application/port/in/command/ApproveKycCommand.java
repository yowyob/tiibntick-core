package com.yowyob.tiibntick.core.tp.application.port.in.command;

import java.util.UUID;

/**
 * Command to approve a submitted KYC record.
 *
 * @author MANFOUO Braun
 */
public record ApproveKycCommand(
        UUID tenantId,
        UUID kycRecordId,
        UUID reviewerAdminId
) {}
