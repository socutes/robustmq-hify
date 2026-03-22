package com.hify.common.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PageResult<T> extends Result<List<T>> {

    private final long total;
    private final int page;
    private final int pageSize;

    private PageResult(List<T> list, long total, int page, int pageSize) {
        super(200, "success", list);
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int pageSize) {
        return new PageResult<>(list, total, page, pageSize);
    }
}
