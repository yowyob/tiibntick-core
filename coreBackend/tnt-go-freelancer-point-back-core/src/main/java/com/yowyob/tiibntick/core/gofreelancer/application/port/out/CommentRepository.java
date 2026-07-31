package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Comment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for comment persistence operations.
 */
public interface CommentRepository {

    Mono<Comment> save(Comment comment);

    Mono<Comment> findById(UUID id);

    Flux<Comment> findAll();

    Mono<Void> deleteById(UUID id);
}
