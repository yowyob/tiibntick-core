package com.yowyob.tiibntick.core.tp.application.port.in.command;

import java.util.UUID;

/**
 * Command to reject a submitted KYC record with a reason.
 *
 * @author MANFOUO Braun
 */
public record RejectKycCommand(
        UUID tenantId,
        UUID kycRecordId,
        UUID reviewerAdminId,
        String rejectionReason
) {}
