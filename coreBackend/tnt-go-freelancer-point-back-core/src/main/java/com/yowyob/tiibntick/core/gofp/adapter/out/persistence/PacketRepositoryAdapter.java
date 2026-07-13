package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PacketEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcPacketRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IPacketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PacketRepositoryAdapter implements IPacketRepository {

    private final R2dbcPacketRepository r2dbc;

    @Override public Mono<PacketEntity> save(PacketEntity e)   { return r2dbc.save(e); }
    @Override public Mono<PacketEntity> findById(UUID id)       { return r2dbc.findById(id); }
    @Override public Mono<Void>         deleteById(UUID id)     { return r2dbc.deleteById(id); }
}
