package com.urbancanopy.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Uniform pagination envelope.
 *
 * <p>Spring's {@code Page} serialises to a large, unstable JSON shape that leaks internal
 * structure ({@code pageable}, {@code sort.sorted}, …). This record exposes only what a
 * client needs to render a pager, and keeps that contract stable regardless of the Spring
 * Data version.</p>
 *
 * @param content       the items on this page, already mapped to DTOs
 * @param page          zero-based page index
 * @param size          page size
 * @param totalElements total matching items across all pages
 * @param totalPages    total number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 * @param <T>           item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /** Wraps a {@code Page} whose contents are already DTOs. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /**
     * Wraps a {@code Page} of entities, mapping each to a DTO.
     *
     * <p>Mapping happens here, inside the caller's transaction, which is what keeps lazy
     * associations from being touched after the session closes ({@code open-in-view} is
     * disabled by design).</p>
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
