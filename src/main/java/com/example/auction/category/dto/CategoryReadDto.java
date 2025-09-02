package com.example.auction.category.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.example.auction.category.domain.Category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryReadDto {
	private Long categoryId;
	private String categoryName;
	private Long parentId;
//    private List<Long> childrenIds;
    
    public static CategoryReadDto fromEntity(Category category) {
        return CategoryReadDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .parentId(category.getParent() != null ? category.getParent().getCategoryId() : null)
//                .childrenIds(
//                		category.getChildren().stream()
//                           .map(Category::getCategoryId)
//                           .collect(Collectors.toList())
//                )
                .build();
    }
}
