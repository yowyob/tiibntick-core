package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Comment;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound adapter bridging the domain CommentRepository port to the reactive R2DBC repository.
 */
@Component
@RequiredArgsConstructor
public class CommentRepositoryAdapter implements CommentRepository {

    private final com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.CommentRepository r2dbcRepository;

    @Override
    public Mono<Comment> save(Comment comment) {
        return r2dbcRepository.save(comment);
    }

    @Override
    public Mono<Comment> findById(UUID id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Flux<Comment> findAll() {
        return r2dbcRepository.findAll();
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }
}
