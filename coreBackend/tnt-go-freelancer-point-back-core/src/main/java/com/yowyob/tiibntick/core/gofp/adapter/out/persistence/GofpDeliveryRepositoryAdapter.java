package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.DeliveryEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.GofpR2dbcDeliveryRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GofpDeliveryRepositoryAdapter implements IDeliveryRepository {

    private final GofpR2dbcDeliveryRepository r2dbc;

    @Override public Mono<DeliveryEntity> save(DeliveryEntity e)                              { return r2dbc.save(e); }
    @Override public Mono<DeliveryEntity> findById(UUID id)                                    { return r2dbc.findById(id); }
    @Override public Mono<DeliveryEntity> findByAnnouncementId(UUID announcementId)            { return r2dbc.findByAnnouncementId(announcementId); }
    @Override public Flux<DeliveryEntity> findByFreelancerActorId(UUID freelancerActorId)      { return r2dbc.findByFreelancerActorId(freelancerActorId); }
    @Override public Flux<DeliveryEntity> findActiveByFreelancerActorId(UUID freelancerActorId){ return r2dbc.findActiveByFreelancerActorId(freelancerActorId); }
    @Override public Flux<DeliveryEntity> findByStatus(String status)                          { return r2dbc.findByStatus(status); }
    @Override public Mono<Void>           deleteById(UUID id)                                  { return r2dbc.deleteById(id); }
}
