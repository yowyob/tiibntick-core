package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.jackson.AddressJsonDeserializer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO utilisant la composition pour combiner le Value Object du Core
 * et les spécificités utilisateur (comme le type d'adresse).
 *
 * @author MANFOUO BRAUN
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDTO {
    
    /**
     * L'identifiant unique de l'adresse en base de données.
     */
    private UUID id;
    
    /**
     * L'adresse géographique pure (le Value Object métier).
     */
    @JsonDeserialize(using = AddressJsonDeserializer.class)
    private Address address;
    
    /**
     * Le type d'adresse défini par l'utilisateur (Domicile, Bureau...).
     */
    private AddressType type;
}
