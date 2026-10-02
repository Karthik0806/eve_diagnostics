package com.evehealthcare.diagnostics.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, R> PageResponse<R> of(Page<S> page, Function<? super S, ? extends R> mapper) {
        List<R> content = page.getContent().stream().<R>map(mapper).toList();
        return from(page, content);
    }

    public static <R> PageResponse<R> from(Page<?> page, List<R> content) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
