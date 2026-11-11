package com.yowyob.tiibntick.core.gofreelancer.domain.model;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/** One message of the negotiation thread of an announcement. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("gofp_negotiation_messages")
public class NegotiationMessage implements Persistable<UUID>, TntPersistableEntity {

    @Transient @Builder.Default private boolean isNew = true;

    @Override public boolean isNew() { return isNew; }
    @Override public void markNotNew() { this.isNew = false; }

    @Id
    @Column("id")
    private UUID id;

    @Column("announcement_id")
    private UUID announcementId;

    /** User id of the delivery person this conversation is with (one thread per announcement and delivery person). */
    @Column("thread_user_id")
    private UUID threadUserId;

    @Column("sender_user_id")
    private UUID senderUserId;

    /** CLIENT or FREELANCER. */
    @Column("sender_role")
    private String senderRole;

    @Column("content")
    private String content;

    @Column("created_at")
    private Instant createdAt;

    /** When the other party saw the message; null while unread. */
    @Column("read_at")
    private Instant readAt;
}
