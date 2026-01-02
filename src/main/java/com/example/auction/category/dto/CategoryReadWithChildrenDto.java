package com.example.auction.category.dto;

import com.example.auction.category.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryReadWithChildrenDto {
    private Long categoryId;
    private String categoryName;
    private List<CategoryReadWithChildrenDto> children;

    // 기본 (자식 없이, productCount 없음)
    public static CategoryReadWithChildrenAndCountDto from(Category category) {
        return CategoryReadWithChildrenAndCountDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .productCount(null) // productCount 제거
                .children(Collections.emptyList())
                .build();
    }

    // 자식 포함 (깊이 제한 없음)
    public static CategoryReadWithChildrenDto fromWithChildren(Category category) {
        return fromWithChildren(category, 0, Integer.MAX_VALUE);
    }

    // 자식 포함 (깊이 제한)
    public static CategoryReadWithChildrenDto fromWithChildren(Category category, int currentDepth, int maxDepth) {
        CategoryReadWithChildrenDtoBuilder builder = CategoryReadWithChildrenDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName());

        if (currentDepth >= maxDepth) {
            builder.children(Collections.emptyList());
        } else {
            List<CategoryReadWithChildrenDto> childrenDTOs = new ArrayList<>(category.getChildren()).stream()
                    .map(child -> fromWithChildren(child, currentDepth + 1, maxDepth))
                    .collect(Collectors.toList());
            builder.children(childrenDTOs);
        }

        return builder.build();
    }
}