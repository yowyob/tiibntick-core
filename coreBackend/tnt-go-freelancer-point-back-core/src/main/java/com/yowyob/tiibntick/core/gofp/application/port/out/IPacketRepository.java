package com.yowyob.tiibntick.core.gofp.application.port.out;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PacketEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IPacketRepository {
    Mono<PacketEntity> save(PacketEntity entity);
    Mono<PacketEntity> findById(UUID id);
    Mono<Void> deleteById(UUID id);
}
