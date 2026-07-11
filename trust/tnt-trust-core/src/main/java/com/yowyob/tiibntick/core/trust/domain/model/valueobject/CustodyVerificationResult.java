package com.yowyob.tiibntick.core.trust.domain.model.valueobject;

import java.time.LocalDateTime;

/**
 * Result of a Chain of Custody cryptographic verification.
 *
 * @author MANFOUO Braun
 */
public record CustodyVerificationResult(
        String packageId,
        boolean chainIntact,
        int linksVerified,
        String brokenAtCustodyHash,
        String reason,
        LocalDateTime verifiedAt
) {}
