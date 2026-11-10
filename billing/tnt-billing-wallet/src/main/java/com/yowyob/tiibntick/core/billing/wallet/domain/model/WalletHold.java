package com.yowyob.tiibntick.core.billing.wallet.domain.model;

import com.yowyob.tiibntick.core.billing.wallet.domain.enums.HoldStatus;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Amount the Core refuses to let {@code ownerUserId} move out of their Kernel wallet. */
@Value
@Builder(toBuilder = true)
@With
public class WalletHold {
    UUID id;
    UUID tenantId;
    UUID ownerUserId;
    String referenceType;
    UUID referenceId;
    BigDecimal amount;
    String currency;
    String reason;
    HoldStatus status;
    LocalDateTime createdAt;
    LocalDateTime releasedAt;
    boolean persisted;
}
