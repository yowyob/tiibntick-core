package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("tnt_dispute_comments")
public class DisputeCommentEntity {

    @Id
    @Column("id")
    private String id;

    @Column("dispute_id")
    private String disputeId;

    @Column("tenant_id")
    private String tenantId;

    @Column("author_id")
    private String authorId;

    @Column("author_type")
    private String authorType;

    @Column("content")
    private String content;

    @Column("is_internal")
    private boolean internal;

    @Column("posted_at")
    private LocalDateTime postedAt;

    public DisputeCommentEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDisputeId() { return disputeId; }
    public void setDisputeId(String disputeId) { this.disputeId = disputeId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isInternal() { return internal; }
    public void setInternal(boolean internal) { this.internal = internal; }
    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }
}
