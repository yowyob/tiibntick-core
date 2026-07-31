package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointSubscription;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayPointSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.RelayPointSubscriptionR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RelayPointSubscriptionRepositoryAdapter implements RelayPointSubscriptionRepository {

    private final RelayPointSubscriptionR2dbcRepository r2dbc;

    @Override public Mono<RelayPointSubscription> save(RelayPointSubscription s)           { return r2dbc.save(s); }
    @Override public Mono<RelayPointSubscription> findById(UUID id)                        { return r2dbc.findById(id); }
    @Override public Mono<RelayPointSubscription> findByRelayPointId(UUID relayPointId)    { return r2dbc.findByRelayPointId(relayPointId); }
    @Override public Mono<Void> deleteById(UUID id)                                        { return r2dbc.deleteById(id); }
}
