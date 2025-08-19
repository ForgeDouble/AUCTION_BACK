package com.example.auction.product.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.example.auction.product.domain.Tag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagReadDto {
	private Long tagId;
	private String tagName;
	private Long parentId;
    private List<Long> childrenIds;
    
    public static TagReadDto fromEntity(Tag tag) {
        return TagReadDto.builder()
                .tagId(tag.getTagId())
                .tagName(tag.getTagName())
                .parentId(tag.getParent() != null ? tag.getParent().getTagId() : null)
                .childrenIds(
                        tag.getChildren().stream()
                           .map(Tag::getTagId)
                           .collect(Collectors.toList())
                )
                .build();
    }
}
