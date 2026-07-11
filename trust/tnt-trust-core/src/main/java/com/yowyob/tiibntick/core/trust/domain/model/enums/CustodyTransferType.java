package com.yowyob.tiibntick.core.trust.domain.model.enums;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Enum — {@code CustodyTransferType}.
 *
 * <p>Describes the nature of a package custody transfer between actors.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public enum CustodyTransferType {
    /** Deliverer picks up the package from the sender. */
    PICKUP_FROM_SENDER,
    /** Deliverer transfers the package to a relay hub. */
    TRANSFER_TO_HUB,
    /** Deliverer picks up a package from a relay hub. */
    PICKUP_FROM_HUB,
    /** Deliverer hands the package to the recipient. */
    TRANSFER_TO_RECIPIENT,
    /** Package is returned to the sender after failed delivery. */
    RETURN_TO_SENDER
}
