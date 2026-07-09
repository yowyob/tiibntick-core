package com.yowyob.tiibntick.core.platformgateway.application.port.out;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ApiKeyRotationRecord;
import reactor.core.publisher.Mono;

/**
 * Secondary (outbound) port for {@link ApiKeyRotationRecord} persistence.
 *
 * @author MANFOUO Braun
 */
public interface IApiKeyRotationRepository {

    Mono<ApiKeyRotationRecord> save(ApiKeyRotationRecord record);
}
