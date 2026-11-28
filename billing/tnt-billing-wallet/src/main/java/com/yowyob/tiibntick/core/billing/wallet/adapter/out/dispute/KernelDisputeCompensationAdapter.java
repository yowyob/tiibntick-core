package com.yowyob.tiibntick.core.billing.wallet.adapter.out.dispute;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICompensationUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.PayCompensationCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeCompensationPort;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeCompensationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The dispute module's compensation port (declared in dispute-core, spec §4.3), paid by the Kernel compensation fund
 * (lot L5). Wallet failures become {@link DisputeCompensationException} with the same code and status, so the dispute
 * stays PENDING_COMPENSATION and the caller sees 409/503.
 */
@Component
public class KernelDisputeCompensationAdapter implements IDisputeCompensationPort {

    public static final String BENEFICIARY_INVALID = "COMPENSATION_BENEFICIARY_INVALID";

    private final ICompensationUseCase compensations;

    public KernelDisputeCompensationAdapter(ICompensationUseCase compensations) {
        this.compensations = compensations;
    }

    @Override
    public Mono<String> pay(String tenantId, String disputeId, String beneficiaryActorId, BigDecimal amount,
                            String currency) {
        UUID beneficiary = uuid(beneficiaryActorId);
        if (beneficiary == null) {
            return Mono.error(new DisputeCompensationException(BENEFICIARY_INVALID, 409,
                    "The compensation beneficiary " + beneficiaryActorId + " is not a Core actor id"));
        }
        UUID tenant = uuid(tenantId);
        return Mono.defer(() -> compensations.payDisputeCompensation(new PayCompensationCommand(
                        tenant != null ? tenant : SystemTenant.id(), disputeId, beneficiary, amount, currency)))
                .map(paid -> paid.kernelCompensationId().toString())
                .onErrorMap(WalletSettlementException.class,
                        e -> new DisputeCompensationException(e.getCode(), e.getHttpStatus(), e.getMessage()));
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
