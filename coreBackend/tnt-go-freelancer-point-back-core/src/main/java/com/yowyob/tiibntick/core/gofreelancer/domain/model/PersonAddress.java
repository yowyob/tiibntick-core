package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.address.AddressType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Association table for the many-to-many relationship between Person and Address.
 * A person can have multiple addresses and an address can belong to multiple persons.
 * The {@code type} field distinguishes the primary (HOME) address from the
 * secondary (AWAY) address for freelancers and relay points.
 *
 * @author Kengfack Lagrange
 * @date 21/01/2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("person_addresses")
public class PersonAddress {

    @Id
    @Column("id")
    private UUID id;

    @NotNull
    @Column("person_id")
    private UUID personId;

    @NotNull
    @Column("address_id")
    private UUID addressId;

    @NotNull
    @Column("address_type")
    private AddressType type;
}
