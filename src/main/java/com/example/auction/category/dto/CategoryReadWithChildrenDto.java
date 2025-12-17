package com.example.auction.category.dto;

import com.example.auction.category.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryReadWithChildrenDto {
    private Long categoryId;
    private String categoryName;
    private Long productCount;
    private List<CategoryReadWithChildrenDto> children;

    // 기본 (자식 없이) - 기존 유지
    public static CategoryReadWithChildrenDto from(Category category) {
        return CategoryReadWithChildrenDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .productCount((long) category.getProducts().size()) // 추가
                .children(Collections.emptyList())
                .build();
    }

    // 자식 포함 (깊이 제한 없음) - productCountMap 없이 사용
    public static CategoryReadWithChildrenDto fromWithChildren(Category category) {
        return fromWithChildren(category, 0, Integer.MAX_VALUE);
    }

    // 자식 포함 (깊이 제한) - productCountMap 없이 사용
    public static CategoryReadWithChildrenDto fromWithChildren(Category category, int currentDepth, int maxDepth) {
        CategoryReadWithChildrenDtoBuilder builder = CategoryReadWithChildrenDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .productCount((long) category.getProducts().size()); // 추가

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

    // 자식 포함 + productCountMap 사용 (효율적인 버전)
    public static CategoryReadWithChildrenDto fromWithChildren(
            Category category, int currentDepth, int maxDepth, Map<Long, Long> productCountMap) {

        CategoryReadWithChildrenDtoBuilder builder = CategoryReadWithChildrenDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .productCount(productCountMap.getOrDefault(category.getCategoryId(), 0L));

        if (currentDepth >= maxDepth) {
            builder.children(Collections.emptyList());
        } else {
            List<CategoryReadWithChildrenDto> childrenDTOs = new ArrayList<>(category.getChildren()).stream()
                    .map(child -> fromWithChildren(child, currentDepth + 1, maxDepth, productCountMap))
                    .collect(Collectors.toList());
            builder.children(childrenDTOs);
        }

        return builder.build();
    }
}