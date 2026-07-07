package com.yowyob.tiibntick.core.notify.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * R2DBC entity mapping for the tnt_notifications table.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tnt_notifications")
public class NotificationEntity {

    @Id
    private String id;
    @Column("destinataire_id")
    private String recipientId;
    @Column("canal")
    private String channel;
    @Column("contenu")
    private String content;
    @Column("statut")
    private String status;
    @Column("priorite")
    private String priority;
    @Column("tentatives")
    private Integer attempts;
    @Column("date_creation")
    private Instant createdAt;
    @Column("date_envoi")
    private Instant sentAt;
    @Column("message_erreur")
    private String errorMessage;
}
