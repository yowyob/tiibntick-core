package com.yowyob.tiibntick.core.billing.wallet.adapter.out.delivery;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy;
import com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Return guarantee of a delivery to a relay hub on the Kernel escrow (decisions D-147, D-151, D-152): a second escrow
 * ({@code RETURN_GUARANTEE}) of the delivery's amount, held at the paid selection with the sender's second code, to
 * the compensation fund.
 * <ul>
 *   <li>{@link #hold}: the guarantee is held at the selection, before the delivery exists — this only reports it
 *       ({@code HELD}), or {@code NOT_HANDLED} / {@code GUARANTEE_NOT_CONFIRMED} when the delivery was elected without
 *       it (election without the client's code, delivery paid afterwards);</li>
 *   <li>{@link #release}: refunded to the sender (parcel collected, delivery ended without return);</li>
 *   <li>{@link #capture}: released to the compensation fund, which then pays the return's courier (the return is
 *       elected in mode FUND).</li>
 * </ul>
 * Idempotent: an escrow already in the target state is returned as is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KernelReturnGuaranteeAdapter implements ReturnGuaranteePort {

    public static final String NOT_CONFIRMED = "GUARANTEE_NOT_CONFIRMED";

    private final IEscrowUseCase escrows;

    @Override
    public Mono<Result> hold(Request request) {
        return escrows.findGuarantee(request.deliveryId())
                .map(g -> g.status() == EscrowMirror.Status.HELD || g.status() == EscrowMirror.Status.FROZEN
                        ? new Result(ReturnGuaranteeStatus.HELD, null) : state(g))
                .defaultIfEmpty(new Result(ReturnGuaranteeStatus.NOT_HANDLED, NOT_CONFIRMED));
    }

    @Override
    public Mono<Result> release(Request request) {
        return escrows.applyGuarantee(request.deliveryId(), EscrowOutcomePolicy.Action.REFUND, "GUARANTEE_RELEASED")
                .map(KernelReturnGuaranteeAdapter::state)
                .defaultIfEmpty(new Result(ReturnGuaranteeStatus.NOT_HANDLED, NOT_CONFIRMED));
    }

    @Override
    public Mono<Result> capture(Request request) {
        return escrows.applyGuarantee(request.deliveryId(), EscrowOutcomePolicy.Action.RELEASE,
                        "GUARANTEE_CAPTURED_FOR_RETURN")
                .map(KernelReturnGuaranteeAdapter::state)
                .defaultIfEmpty(new Result(ReturnGuaranteeStatus.NOT_HANDLED, NOT_CONFIRMED));
    }

    private static Result state(EscrowMirror g) {
        return switch (g.status()) {
            case HELD, FROZEN -> new Result(ReturnGuaranteeStatus.HELD, null);
            case REFUNDED -> new Result(ReturnGuaranteeStatus.RELEASED, null);
            case RELEASED -> new Result(ReturnGuaranteeStatus.CAPTURED, null);
        };
    }
}
