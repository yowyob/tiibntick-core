package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.ClientUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ClientDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ClientResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Inbound REST adapter for client management.
 * Delegates to the ClientUseCase inbound port.
 */
@RestController("gofpLegacyClientController")
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientUseCase clientUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ClientResponseDTO> createClient(@RequestBody ClientDTO clientDTO) {
        return clientUseCase.createClient(clientDTO);
    }

    @GetMapping("/check-email")
    public Mono<Boolean> checkEmail(@RequestParam String email) {
        return clientUseCase.checkEmailExists(email);
    }

    @GetMapping("/check-national-id")
    public Mono<Boolean> checkNationalId(@RequestParam String nationalId) {
        return clientUseCase.checkNationalIdExists(nationalId);
    }

    @GetMapping
    public Flux<ClientResponseDTO> getAllClients() {
        return clientUseCase.getAllClients();
    }

    @GetMapping("/{id}")
    public Mono<ClientResponseDTO> getClientById(@PathVariable UUID id) {
        return clientUseCase.getClientById(id);
    }

    @PutMapping("/{id}")
    public Mono<ClientResponseDTO> updateClient(@PathVariable UUID id, @RequestBody ClientDTO clientDTO) {
        return clientUseCase.updateClient(id, clientDTO);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteClient(@PathVariable UUID id) {
        return clientUseCase.deleteClient(id);
    }

    /**
     * Uploads or replaces the profile photo of a client.
     *
     * Request body: { "profilePhoto": "data:image/jpeg;base64,..." }
     *
     * Returns the updated client with the new profilePhoto URL.
     */
    @PatchMapping("/{id}/profile-photo")
    public Mono<ResponseEntity<ClientResponseDTO>> updateProfilePhoto(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String base64Photo = body.get("profilePhoto");
        if (base64Photo == null || base64Photo.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return clientUseCase.updateProfilePhoto(id, base64Photo)
                .map(ResponseEntity::ok);
    }

    /**
     * Removes the profile photo of a client.
     */
    @DeleteMapping("/{id}/profile-photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteProfilePhoto(@PathVariable UUID id) {
        return clientUseCase.deleteProfilePhoto(id);
    }
}
