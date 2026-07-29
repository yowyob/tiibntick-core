package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a password reset token.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("password_tokens")
public class PasswordToken {

    @Id
    @Column("id")
    private UUID id;

    @Column("person_id")
    private UUID personId;

    @Column("token")
    private String token;

    @Column("expiry_date")
    private Instant expiryDate;

    @Column("used")
    private Boolean used;
}
