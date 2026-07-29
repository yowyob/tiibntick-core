package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter: bridges IAnnouncementRepository to the R2DBC Spring Data repository.
 */
@Component
@RequiredArgsConstructor
public class AnnouncementRepositoryAdapter implements IAnnouncementRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AnnouncementRepository r2dbcRepository;

    @Override
    public Mono<Announcement> save(Announcement announcement) {
        return r2dbcRepository.save(announcement);
    }

    @Override
    public Mono<Announcement> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Flux<Announcement> findAll() {
        return r2dbcRepository.findAll();
    }

    @Override
    public Flux<Announcement> findAllByClientId(UUID clientId) {
        return r2dbcRepository.findAllByClientId(clientId);
    }

    @Override
    public Flux<Announcement> findAllByStatus(AnnouncementStatus status) {
        return r2dbcRepository.findAllByStatus(status);
    }

    @Override
    public Flux<Announcement> findAllByAssignedFreelancerId(UUID freelancerId) {
        return r2dbcRepository.findAllByAssignedFreelancerId(freelancerId);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
