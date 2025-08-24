package com.example.auction.category.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.category.domain.Category;
import com.example.auction.category.dto.CategoryCreateDto;
import com.example.auction.category.dto.CategoryReadDto;
import com.example.auction.category.repository.CategoryRepository;
import com.example.auction.product.domain.Product;
import com.example.auction.product.dto.ProductCreateDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {
	
	private final CategoryRepository categoryRepository;
	
	// 아이템 생성
	@Transactional
	public Category createCategory(CategoryCreateDto dto) {
		Category parent = null;
		 if (dto.getParentId() != null) {
	            parent = categoryRepository.findById(dto.getParentId())
	                                  .orElseThrow(() -> new RuntimeException("부모 태그가 존재하지 않습니다."));
	    }
		Category tag = dto.toCategory(parent);
		return categoryRepository.save(tag);
	}
	
//	@Transactional
//	public void deleteTag() {
//		
//	}
	
	// 태그 단일 조회
	@Transactional
	public CategoryReadDto getCategory(Long categoryId) {
        Category category = categoryRepository.findByIdWithChildren(categoryId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 태그입니다."));
        return CategoryReadDto.fromEntity(category);
    }
	
	// 태그 목록 조회
	 public List<CategoryReadDto> getAllCategories() {
		 List<Category> categories = categoryRepository.findAllWithChildren();
		 return categories.stream()
	                .map(CategoryReadDto::fromEntity)
	                .toList();
	}
}
