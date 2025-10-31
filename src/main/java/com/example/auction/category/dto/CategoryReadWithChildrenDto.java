package com.example.auction.category.dto;

import com.example.auction.category.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    // 기본 (자식 없이)
    public static CategoryReadWithChildrenDto from(Category category) {
        return CategoryReadWithChildrenDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .children(Collections.emptyList())
                .build();
    }

    // 자식 포함 (깊이 제한 없음 - 내부적으로 MAX_VALUE 사용)
    public static CategoryReadWithChildrenDto fromWithChildren(Category category) {
        return fromWithChildren(category, 0, Integer.MAX_VALUE);
    }

    // 깊이 제한 포함 (메인 메서드)
    public static CategoryReadWithChildrenDto fromWithChildren(Category category, int currentDepth, int maxDepth) {
        CategoryReadWithChildrenDtoBuilder builder = CategoryReadWithChildrenDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName());

        // 최대 깊이에 도달하면 children을 빈 리스트로
        if (currentDepth >= maxDepth) {
            builder.children(Collections.emptyList());
        } else {
            // 자식들을 재귀적으로 변환 (깊이 +1)
            List<CategoryReadWithChildrenDto> childrenDTOs = category.getChildren().stream()
                    .map(child -> fromWithChildren(child, currentDepth + 1, maxDepth))
                    .collect(Collectors.toList());
            builder.children(childrenDTOs);
        }

        return builder.build();
    }
}