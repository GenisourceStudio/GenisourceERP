package com.genisource.erp.platform.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Wire format for paged collections.
 *
 * <p>Spring's own {@code Page} serialises its internal {@code Pageable} and
 * {@code Sort} objects, which are an implementation detail no client should depend on.
 * This envelope is deliberately flat and stable so the SPA can keep one list hook for
 * every module.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long total,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    public static <T, R> PageResponse<R> of(Page<T> page, java.util.function.Function<T, R> mapper) {
        return of(page.map(mapper));
    }

    /**
     * Wraps an already-projected list with the paging metadata of the source page.
     *
     * <p>Needed when mapping a page of entities requires batch lookups (category names,
     * supplier names) that would otherwise run once per row inside {@code page.map}.
     */
    public static <R> PageResponse<R> of(Page<?> page, List<R> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, false);
    }
}
