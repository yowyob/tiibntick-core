package com.yowyob.tiibntick.common.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Universal paginated result container, compatible with Spring Data's
 * {@code Page<T>} semantics while remaining framework-agnostic.
 *
 * <p>Used as the {@code data} payload inside {@link ApiResponse} for all
 * list/search endpoints across TiiBnTick platforms.
 *
 * Author: MANFOUO Braun
 *
 * @param <T> type of the page content items
 */
public final class PagedResult<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    private PagedResult(List<T> content, int page, int size, long totalElements) {
        this.content       = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
        this.page          = page;
        this.size          = size;
        this.totalElements = totalElements;
        this.totalPages    = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        this.first         = page == 0;
        this.last          = page >= totalPages - 1;
    }

    /**
     * Creates a paged result.
     *
     * @param content       current page items
     * @param page          0-based page index
     * @param size          requested page size
     * @param totalElements total number of elements across all pages
     */
    public static <T> PagedResult<T> of(List<T> content, int page, int size, long totalElements) {
        return new PagedResult<>(content, page, size, totalElements);
    }

    /**
     * Creates an empty paged result.
     */
    public static <T> PagedResult<T> empty(int page, int size) {
        return new PagedResult<>(Collections.emptyList(), page, size, 0L);
    }

    /**
     * Creates a single-page result where all elements fit on one page.
     */
    public static <T> PagedResult<T> singlePage(List<T> content) {
        return new PagedResult<>(content, 0, content.size(), content.size());
    }

    /**
     * Maps the content of this page using {@code mapper}.
     *
     * @param mapper transformation function
     * @param <U>    target type
     */
    public <U> PagedResult<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<U> mapped = content.stream().map(mapper).collect(Collectors.toList());
        return new PagedResult<>(mapped, page, size, totalElements);
    }

    public List<T> getContent()       { return content; }
    public int getPage()              { return page; }
    public int getSize()              { return size; }
    public long getTotalElements()    { return totalElements; }
    public int getTotalPages()        { return totalPages; }
    public boolean isFirst()          { return first; }
    public boolean isLast()           { return last; }
    public boolean isEmpty()          { return content.isEmpty(); }
    public int getNumberOfElements()  { return content.size(); }

    @Override
    public String toString() {
        return "PagedResult{page=" + page + "/" + totalPages
                + ", size=" + content.size() + "/" + totalElements + "}";
    }
}
