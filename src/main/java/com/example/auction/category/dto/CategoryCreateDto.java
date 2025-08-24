package com.example.auction.category.dto;

import com.example.auction.category.domain.Category;
import com.example.auction.product.domain.Product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryCreateDto {
	private String categoryName;
	private Long parentId;
	
	 public Category toCategory(Category parent) {
	    	return Category.builder()
	    			.categoryName(categoryName)
	    			.parent(parent)
	    			.build();
	    }
}
