package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound REST adapter for address management.
 * Delegates to the AddressUseCase inbound port.
 *
 * @author François-Charles ATANGA — refactored 08/07/2026
 */
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressUseCase addressUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AddressDTO> createAddress(@RequestBody AddressDTO request) {
        return addressUseCase.createAddress(request);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<AddressDTO>> getAddressById(@PathVariable UUID id) {
        return addressUseCase.getAddressById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<AddressDTO> getAllAddresses() {
        return addressUseCase.getAllAddresses();
    }

    @GetMapping("/search")
    public Flux<AddressDTO> searchAddresses(@RequestParam String query) {
        return addressUseCase.searchAddresses(query);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<AddressDTO>> updateAddress(
            @PathVariable UUID id, @RequestBody AddressDTO request) {
        return addressUseCase.updateAddress(id, request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteAddress(@PathVariable UUID id) {
        return addressUseCase.deleteAddress(id);
    }
}
