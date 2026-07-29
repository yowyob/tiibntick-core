package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity.AddressEntity;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AddressReactiveRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.AddressUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service applicatif gérant les adresses et faisant le lien
 * entre les requêtes web (AddressDTO) et la base de données (AddressEntity).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressApplicationService implements AddressUseCase {

    private final AddressReactiveRepository addressRepository;
    private final com.yowyob.tiibntick.core.gofreelancer.application.port.out.IGeolocationPort geolocationPort;

    @Override
    public Mono<AddressDTO> createAddress(AddressDTO dto) {
        log.info("Création d'une nouvelle adresse");
        AddressEntity entity = toEntity(dto);
        // Si le client n'a pas fourni d'ID, on laisse la BDD le générer (ou on le génère ici)
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        return addressRepository.save(entity).map(this::toDto);
    }

    @Override
    public Mono<AddressDTO> getAddressById(UUID id) {
        return addressRepository.findById(id).map(this::toDto);
    }

    @Override
    public Flux<AddressDTO> getAllAddresses() {
        return addressRepository.findAll().map(this::toDto);
    }

    @Override
    public Mono<AddressDTO> updateAddress(UUID id, AddressDTO dto) {
        log.info("Mise à jour de l'adresse {}", id);
        return addressRepository.findById(id)
                .flatMap(existing -> {
                    AddressEntity updated = toEntity(dto);
                    updated.setId(existing.getId()); // on conserve l'ID existant
                    return addressRepository.save(updated);
                })
                .map(this::toDto);
    }

    @Override
    public Mono<Void> deleteAddress(UUID id) {
        log.info("Suppression de l'adresse {}", id);
        return addressRepository.deleteById(id);
    }

    @Override
    public Flux<AddressDTO> searchAddresses(String query) {
        log.info("Recherche d'adresse pour : {}", query);
        
        Flux<AddressDTO> localResults = addressRepository.findByLandmarkContainingIgnoreCaseOrStreetContainingIgnoreCase(query, query)
                .map(this::toDto);
                
        // En cas de liste vide, on fait un fallback vers le service de géolocalisation externe
        return localResults.switchIfEmpty(
                geolocationPort.getCoordinatesFromAddress(query)
                        .map(coords -> {
                            // Création d'une adresse virtuelle basée sur le résultat du géocodeur
                            GeoCoordinates geo = GeoCoordinates.of(coords[0], coords[1]);
                            Address addressVO = Address.builder()
                                    .street(query) // On utilise la requête comme nom de rue
                                    .coordinates(geo)
                                    .build();
                            return new AddressDTO(UUID.randomUUID(), addressVO, null);
                        })
                        .flux() // Convertit le Mono en Flux
        );
    }

    // --- MAPPERS ---

    private AddressEntity toEntity(AddressDTO dto) {
        AddressEntity.AddressEntityBuilder builder = AddressEntity.builder()
                .id(dto.getId())
                .type(dto.getType() != null ? dto.getType().name() : null);

        if (dto.getAddress() != null) {
            builder.street(dto.getAddress().getStreet().orElse(null))
                   .landmark(dto.getAddress().getLandmark().orElse(null))
                   .quarter(dto.getAddress().getQuarter().orElse(null))
                   .city(dto.getAddress().getCity())
                   .country(dto.getAddress().getCountry())
                   .region(dto.getAddress().getRegion().orElse(null))
                   .postalCode(dto.getAddress().getPostalCode().orElse(null));

            if (dto.getAddress().getCoordinates().isPresent()) {
                builder.latitude(dto.getAddress().getCoordinates().get().getLatitude())
                       .longitude(dto.getAddress().getCoordinates().get().getLongitude());
            }
        }
        return builder.build();
    }

    private AddressDTO toDto(AddressEntity entity) {
        GeoCoordinates coords = null;
        if (entity.getLatitude() != null && entity.getLongitude() != null) {
            coords = GeoCoordinates.of(entity.getLatitude(), entity.getLongitude());
        }

        Address addressVO = Address.builder()
                .street(entity.getStreet())
                .landmark(entity.getLandmark())
                .quarter(entity.getQuarter())
                .city(entity.getCity())
                .country(entity.getCountry())
                .region(entity.getRegion())
                .postalCode(entity.getPostalCode())
                .coordinates(coords)
                .build();

        com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType type = null;
        if (entity.getType() != null) {
            try {
                type = com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType.valueOf(entity.getType());
            } catch (Exception ignored) {}
        }

        return new AddressDTO(entity.getId(), addressVO, type);
    }
}
