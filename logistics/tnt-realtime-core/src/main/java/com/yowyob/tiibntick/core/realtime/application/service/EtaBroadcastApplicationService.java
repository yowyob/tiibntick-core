package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IBroadcastEtaUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.LiveETAUpdate;
import com.yowyob.tiibntick.core.realtime.domain.model.ReroutingAlert;
import com.yowyob.tiibntick.core.realtime.domain.service.EtaBroadcastDomainService;
import com.yowyob.tiibntick.core.realtime.domain.service.SseDomainService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Application service for ETA broadcast use cases.
 * Orchestrates WebSocket broadcast and SSE emission.
 *
 * @author MANFOUO Braun
 */
@Service
public class EtaBroadcastApplicationService implements IBroadcastEtaUseCase {

    private final EtaBroadcastDomainService etaBroadcastDomainService;
    private final SseDomainService sseDomainService;

    public EtaBroadcastApplicationService(EtaBroadcastDomainService etaBroadcastDomainService,
                                          SseDomainService sseDomainService) {
        this.etaBroadcastDomainService = etaBroadcastDomainService;
        this.sseDomainService = sseDomainService;
    }

    @Override
    public Mono<Void> broadcastEtaUpdate(LiveETAUpdate etaUpdate) {
        return Mono.when(
                etaBroadcastDomainService.broadcastEta(etaUpdate),
                sseDomainService.publishEtaToSse(etaUpdate)
        );
    }

    @Override
    public Mono<Void> broadcastReroutingAlert(ReroutingAlert alert) {
        return etaBroadcastDomainService.broadcastRerouting(alert);
    }
}
