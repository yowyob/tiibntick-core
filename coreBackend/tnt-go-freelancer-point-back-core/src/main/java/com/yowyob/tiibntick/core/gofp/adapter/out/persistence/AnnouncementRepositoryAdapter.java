package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.GofpR2dbcAnnouncementRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IAnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnnouncementRepositoryAdapter implements IAnnouncementRepository {

    private final GofpR2dbcAnnouncementRepository r2dbc;

    @Override public Mono<AnnouncementEntity> save(AnnouncementEntity e)               { return r2dbc.save(e); }
    @Override public Mono<AnnouncementEntity> findById(UUID id)                         { return r2dbc.findById(id); }
    @Override public Flux<AnnouncementEntity> findByClientActorId(UUID clientActorId)   { return r2dbc.findByClientActorId(clientActorId); }
    @Override public Flux<AnnouncementEntity> findByStatus(String status)               { return r2dbc.findByStatus(status); }
    @Override public Mono<Void>               deleteById(UUID id)                        { return r2dbc.deleteById(id); }
}
