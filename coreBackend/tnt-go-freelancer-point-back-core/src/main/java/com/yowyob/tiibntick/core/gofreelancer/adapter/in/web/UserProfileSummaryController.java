package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.UserProfileSummaryDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.GofpClientUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.GofpFreelancerUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.GofpRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.GofpUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Controller to fetch a summary of all profiles associated with a given core user.
 * This is particularly useful for the frontend to know what profiles the user already has,
 * to skip registration steps for existing data.
 */
@RestController
@RequestMapping("/api/v1/gofp/users")
@RequiredArgsConstructor
public class UserProfileSummaryController {

    private final GofpUserUseCase userUseCase;
    private final GofpClientUseCase clientUseCase;
    private final GofpFreelancerUseCase freelancerUseCase;
    private final GofpRelayPointUseCase relayPointUseCase;

    @GetMapping("/{coreUserId}/profiles-summary")
    public Mono<ResponseEntity<UserProfileSummaryDTO>> getProfilesSummary(@PathVariable UUID coreUserId) {
        return userUseCase.findByCoreUserId(coreUserId)
                .flatMap(user -> {
                    UserProfileSummaryDTO summary = UserProfileSummaryDTO.builder()
                            .coreUserId(user.getCoreUserId())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .cniNumber(user.getCniNumber())
                            .nui(user.getNui())
                            .profilePhotoUrl(user.getProfilePhotoUrl())
                            .build();

                    return clientUseCase.findByCoreUserId(coreUserId)
                            .map(client -> {
                                summary.setClient(true);
                                summary.setClientId(client.getId());
                                return summary;
                            })
                            .defaultIfEmpty(summary)
                            .flatMap(s -> freelancerUseCase.findByCoreUserId(coreUserId)
                                    .flatMap(freelancer -> {
                                        s.setFreelancer(true);
                                        s.setFreelancerId(freelancer.getId());
                                        return relayPointUseCase.findByFreelancer(freelancer.getCoreFreelancerId())
                                                .collectList()
                                                .map(relayPoints -> {
                                                    if (!relayPoints.isEmpty()) {
                                                        s.setRelayPoint(true);
                                                        // if the user has multiple relay points, we just expose the first one's ID for fast navigation
                                                        s.setRelayPointId(relayPoints.get(0).getId());
                                                    }
                                                    return s;
                                                });
                                    })
                                    .defaultIfEmpty(s)
                            );
                })
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }
}
