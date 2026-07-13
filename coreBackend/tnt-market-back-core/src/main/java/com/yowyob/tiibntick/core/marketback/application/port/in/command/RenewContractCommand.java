package com.yowyob.tiibntick.core.marketback.application.port.in.command;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Command — renew a MerchantContract with a new end date.
 * @author MANFOUO Braun
 */
public record RenewContractCommand(@NotNull LocalDate newEndDate) {}
