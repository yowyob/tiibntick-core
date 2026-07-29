package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.Contact;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.r2dbc.repository.Query;

@Repository
public interface ContactRepository extends R2dbcRepository<Contact, UUID> {
    Mono<Contact> findByUserIdAndFirstNameIgnoreCaseAndLastNameIgnoreCase(UUID userId, String firstName, String lastName);

    Flux<Contact> findAllByUserId(UUID userId);

    @Query("SELECT * FROM contacts WHERE user_id = :userId AND (first_name ILIKE CONCAT('%', :searchTerm, '%') OR last_name ILIKE CONCAT('%', :searchTerm, '%'))")
    Flux<Contact> searchContacts(UUID userId, String searchTerm);
}
