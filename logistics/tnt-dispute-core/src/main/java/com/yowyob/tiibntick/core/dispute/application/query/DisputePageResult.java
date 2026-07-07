package com.yowyob.tiibntick.core.dispute.application.query;

import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;

import java.util.List;
import java.util.Objects;

/**
 * Paginated result container for dispute list queries.
 *
 * @author MANFOUO Braun
 */
public record DisputePageResult(
        List<Dispute> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public DisputePageResult {
        content = Objects.requireNonNull(content, "content must not be null");
    }

    public boolean hasNextPage() {
        return page < totalPages - 1;
    }

    public boolean hasPreviousPage() {
        return page > 0;
    }
}
