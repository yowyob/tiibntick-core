package com.yowyob.tiibntick.core.tp.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity for ThirdPartyRating persistence.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_tp_ratings")
public class ThirdPartyRatingEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    @Column("tenant_id") private UUID tenantId;
    @Column("rated_third_party_id") private UUID ratedThirdPartyId;
    @Column("rater_actor_id") private UUID raterActorId;
    @Column("mission_id") private String missionId;
    @Column("score") private double score;
    @Column("comment") private String comment;
    @Column("created_at") private Instant createdAt;

    public ThirdPartyRatingEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getRatedThirdPartyId() { return ratedThirdPartyId; }
    public void setRatedThirdPartyId(UUID ratedThirdPartyId) { this.ratedThirdPartyId = ratedThirdPartyId; }
    public UUID getRaterActorId() { return raterActorId; }
    public void setRaterActorId(UUID raterActorId) { this.raterActorId = raterActorId; }
    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
