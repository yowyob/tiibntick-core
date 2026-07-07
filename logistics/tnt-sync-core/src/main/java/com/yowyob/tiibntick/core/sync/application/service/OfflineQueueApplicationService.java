package com.yowyob.tiibntick.core.sync.application.service;

import com.yowyob.tiibntick.core.sync.application.port.in.IEnqueueOfflineOpUseCase;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationRepository;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class OfflineQueueApplicationService implements IEnqueueOfflineOpUseCase {

    private static final Logger log = LoggerFactory.getLogger(OfflineQueueApplicationService.class);

    private final IOfflineOperationRepository operationRepository;

    public OfflineQueueApplicationService(IOfflineOperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    @Override
    public Mono<Void> enqueue(OfflineOperation operation) {
        log.debug("Enqueuing offline operation {} (type={})", operation.getId(), operation.getType());
        return operationRepository.save(operation).then();
    }
}
