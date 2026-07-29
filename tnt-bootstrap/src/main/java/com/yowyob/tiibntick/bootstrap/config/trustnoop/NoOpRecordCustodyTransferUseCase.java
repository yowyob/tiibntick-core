package com.yowyob.tiibntick.bootstrap.config.trustnoop;

import com.yowyob.tiibntick.core.trust.application.port.in.RecordCustodyTransferUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import reactor.core.publisher.Mono;

/**
 * No-op fallback for {@link RecordCustodyTransferUseCase}, wired only when
 * {@code tnt.trust.enabled=false} — see {@code TrustNoOpFallbackConfig}.
 *
 * @author MANFOUO Braun
 */
public class NoOpRecordCustodyTransferUseCase implements RecordCustodyTransferUseCase {

    @Override
    public Mono<String> record(final CustodyTransferRecord transfer) {
        return Mono.empty();
    }
}
