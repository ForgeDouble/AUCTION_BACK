package com.example.auction.category.dto;

import com.example.auction.category.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*경로 확인 필요*/
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryBasicDto {
    private Long categoryId;
    private String categoryName;

    public static CategoryBasicDto fromEntity(Category category) {
        return CategoryBasicDto.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .build();
    }


}