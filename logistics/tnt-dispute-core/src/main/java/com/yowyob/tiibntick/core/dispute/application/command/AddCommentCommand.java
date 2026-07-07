package com.yowyob.tiibntick.core.dispute.application.command;

import com.yowyob.tiibntick.core.dispute.domain.enums.CommentAuthorType;
import com.yowyob.tiibntick.core.dispute.domain.model.DisputeId;

import java.util.Objects;

/**
 * Command to post a comment on an active dispute's thread.
 *
 * @author MANFOUO Braun
 */
public record AddCommentCommand(
        DisputeId disputeId,
        String tenantId,
        String authorId,
        CommentAuthorType authorType,
        String content,
        boolean isInternal
) {
    public AddCommentCommand {
        Objects.requireNonNull(disputeId, "disputeId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(authorId, "authorId is required");
        Objects.requireNonNull(authorType, "authorType is required");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Comment content must not be blank");
        }
    }
}
