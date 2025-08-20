package com.example.auction.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Tag;
import com.example.auction.product.dto.ProductCreateDto;
import com.example.auction.product.dto.TagCreateDto;
import com.example.auction.product.dto.TagReadDto;
import com.example.auction.product.repository.TagRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagService {
	
	private final TagRepository tagRepository;
	
	// 아이템 생성
	@Transactional
	public Tag createTag(TagCreateDto dto) {
		Tag parent = null;
		 if (dto.getParentId() != null) {
	            parent = tagRepository.findById(dto.getParentId())
	                                  .orElseThrow(() -> new RuntimeException("부모 태그가 존재하지 않습니다."));
	    }
		Tag tag = dto.toTag(parent);
		return tagRepository.save(tag);
	}
	
//	@Transactional
//	public void deleteTag() {
//		
//	}
	
	// 태그 단일 조회
	@Transactional
	public TagReadDto getTag(Long tagId) {
        Tag tag = tagRepository.findByIdWithChildren(tagId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 태그입니다."));
        return TagReadDto.fromEntity(tag);
    }
	
	// 태그 목록 조회
	 public List<TagReadDto> getAllTags() {
		 List<Tag> tags = tagRepository.findAllWithChildren();
		 return tags.stream()
	                .map(TagReadDto::fromEntity)
	                .toList();
	}
}
