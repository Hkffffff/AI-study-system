package com.aistudy.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Paged list response: {@code {items, page, size, total}} with a 0-based {@code page}. */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public static <E, T> PageResult<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> items = page.getContent().stream().<T>map(mapper).toList();
        return new PageResult<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
