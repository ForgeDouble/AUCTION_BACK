package com.example.auction.user.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageUserListDto<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrev
) {
    public static <T> PageUserListDto<T> from(Page<T> page) {
        return new PageUserListDto<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}

