package com.yowyob.tiibntick.core.gofp.adapter.in.web;

import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.AnnouncementEntity;
import com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity.PacketEntity;
import com.yowyob.tiibntick.core.gofp.application.port.in.IAnnouncementUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Tag(name = "GOFP — Annonces", description = "API métier générique — Annonces TiiBnPick")
@RestController
@RequestMapping("/api/v1/gofp/announcements")
@RequiredArgsConstructor
public class AnnouncementCoreController {

    private final IAnnouncementUseCase announcementUseCase;

    @Operation(summary = "Créer une annonce avec son colis")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AnnouncementEntity> create(@RequestBody Map<String, Object> body) {
        // Les DTOs complets seront ajoutés — simplification initiale avec entité directe
        AnnouncementEntity ann    = new AnnouncementEntity();
        PacketEntity       packet = new PacketEntity();
        // mapping body → ann + packet sera fait dans un mapper dédié
        return announcementUseCase.createAnnouncement(ann, packet);
    }

    @Operation(summary = "Récupérer une annonce par ID")
    @GetMapping("/{id}")
    public Mono<AnnouncementEntity> findById(@PathVariable UUID id) {
        return announcementUseCase.findById(id);
    }

    @Operation(summary = "Lister les annonces d'un client")
    @GetMapping("/client/{clientActorId}")
    public Flux<AnnouncementEntity> findByClient(@PathVariable UUID clientActorId) {
        return announcementUseCase.findByClientActorId(clientActorId);
    }

    @Operation(summary = "Lister les annonces publiées (en attente de livreur)")
    @GetMapping("/published")
    public Flux<AnnouncementEntity> findPublished() {
        return announcementUseCase.findPublished();
    }

    @Operation(summary = "Publier une annonce DRAFT")
    @PatchMapping("/{id}/publish")
    public Mono<AnnouncementEntity> publish(@PathVariable UUID id) {
        return announcementUseCase.publishAnnouncement(id);
    }

    @Operation(summary = "Assigner un livreur à une annonce")
    @PatchMapping("/{id}/assign/{freelancerActorId}")
    public Mono<AnnouncementEntity> assign(@PathVariable UUID id,
                                            @PathVariable UUID freelancerActorId) {
        return announcementUseCase.assignFreelancer(id, freelancerActorId);
    }

    @Operation(summary = "Annuler une annonce")
    @PatchMapping("/{id}/cancel")
    public Mono<AnnouncementEntity> cancel(@PathVariable UUID id) {
        return announcementUseCase.cancelAnnouncement(id);
    }
}
