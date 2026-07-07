package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.domain.enums.CommentAuthorType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a comment posted on a dispute thread by a party or mediator.
 *
 * <p>Comments are visible to all involved parties unless marked as internal.
 * Internal comments are only visible to mediators and administrators.
 *
 * @author MANFOUO Braun
 */
public final class DisputeComment {

    private final String id;
    private final DisputeId disputeId;
    private final String authorId;
    private final CommentAuthorType authorType;
    private final String content;
    private final boolean isInternal;
    private final LocalDateTime postedAt;

    private DisputeComment(
            final String id,
            final DisputeId disputeId,
            final String authorId,
            final CommentAuthorType authorType,
            final String content,
            final boolean isInternal,
            final LocalDateTime postedAt) {
        this.id = Objects.requireNonNull(id);
        this.disputeId = Objects.requireNonNull(disputeId);
        this.authorId = Objects.requireNonNull(authorId);
        this.authorType = Objects.requireNonNull(authorType);
        this.content = Objects.requireNonNull(content);
        this.isInternal = isInternal;
        this.postedAt = Objects.requireNonNull(postedAt);
    }

    public static DisputeComment post(
            final DisputeId disputeId,
            final String authorId,
            final CommentAuthorType authorType,
            final String content,
            final boolean isInternal) {
        return new DisputeComment(UUID.randomUUID().toString(), disputeId, authorId, authorType,
                content, isInternal, LocalDateTime.now());
    }

    public static DisputeComment reconstitute(
            final String id,
            final DisputeId disputeId,
            final String authorId,
            final CommentAuthorType authorType,
            final String content,
            final boolean isInternal,
            final LocalDateTime postedAt) {
        return new DisputeComment(id, disputeId, authorId, authorType, content, isInternal, postedAt);
    }

    public String getId() { return id; }
    public DisputeId getDisputeId() { return disputeId; }
    public String getAuthorId() { return authorId; }
    public CommentAuthorType getAuthorType() { return authorType; }
    public String getContent() { return content; }
    public boolean isInternal() { return isInternal; }
    public LocalDateTime getPostedAt() { return postedAt; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeComment that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
