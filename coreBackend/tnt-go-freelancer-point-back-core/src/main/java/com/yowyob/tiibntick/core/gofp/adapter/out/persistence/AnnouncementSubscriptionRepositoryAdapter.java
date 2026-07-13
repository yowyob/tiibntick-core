package com.yowyob.tiibntick.core.gofp.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementSubscriptionEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.repository.R2dbcAnnouncementSubscriptionRepository;
import com.yowyob.tiibntick.core.gofp.application.port.out.IAnnouncementSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnnouncementSubscriptionRepositoryAdapter implements IAnnouncementSubscriptionRepository {

    private final R2dbcAnnouncementSubscriptionRepository r2dbc;

    @Override public Mono<AnnouncementSubscriptionEntity> save(AnnouncementSubscriptionEntity e)                                              { return r2dbc.save(e); }
    @Override public Mono<AnnouncementSubscriptionEntity> findById(UUID id)                                                                    { return r2dbc.findById(id); }
    @Override public Flux<AnnouncementSubscriptionEntity> findByAnnouncementId(UUID announcementId)                                            { return r2dbc.findByAnnouncementId(announcementId); }
    @Override public Flux<AnnouncementSubscriptionEntity> findByFreelancerActorId(UUID freelancerActorId)                                      { return r2dbc.findByFreelancerActorId(freelancerActorId); }
    @Override public Mono<AnnouncementSubscriptionEntity> findByAnnouncementIdAndFreelancerActorId(UUID announcementId, UUID freelancerActorId) { return r2dbc.findByAnnouncementIdAndFreelancerActorId(announcementId, freelancerActorId); }
    @Override public Flux<AnnouncementSubscriptionEntity> findByAnnouncementIdAndStatus(UUID announcementId, String status)                    { return r2dbc.findByAnnouncementIdAndStatus(announcementId, status); }
}
