package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "tnt_link", value = "dao_proposals")
public class DaoProposalEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;

    @Column("zone_id")
    private UUID zoneId;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("proposer_id")
    private UUID proposerId;

    @Column("status")
    private String status;

    @Column("votes_for")
    private int votesFor;

    @Column("votes_against")
    private int votesAgainst;

    @Column("voting_deadline")
    private Instant votingDeadline;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Version
    private long version;
}
