package com.example.auction.product.dto;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Tag;

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
public class TagCreateDto {
	private String tagName;
	private Long parentId;
	
	 public Tag toTag(Tag parent) {
	    	return Tag.builder()
	    			.tagName(tagName)
	    			.parent(parent)
	    			.build();
	    }
}
