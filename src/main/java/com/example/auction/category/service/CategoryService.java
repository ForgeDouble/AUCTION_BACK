package com.example.auction.category.service;

import java.util.List;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserRepository userRepository;
	
	// 카테고리 생성 [관리자]
	@Transactional
	public Category createCategory(CategoryCreateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));
        if (user.getAuthority() != Authority.ADMIN) {
            throw new UnauthorizedAccessException("관리자 외 권한이 없습니다.");
        }

		Category parent = null;
		 if (dto.getParentId() != null) {
	            parent = categoryRepository.findById(dto.getParentId())
	                                  .orElseThrow(() -> new ResourceNotFoundException("부모 Category"));
	    }
		Category category = dto.toCategory(parent);
		return categoryRepository.save(category);
	}
	
//	@Transactional
//	public void deleteTag() {
//		
//	}
	
	// 카테고리 단일 조회
    // 사용 안할 수 도 있음
	@Transactional
	public CategoryReadDto getCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 태그입니다."));
        return CategoryReadDto.fromEntity(category);
    }
	
	// 카테고리 목록 조회
    @Transactional
	 public List<CategoryReadDto> getAllCategories() {
		 List<Category> categories = categoryRepository.findAll();
		 return categories.stream()
	                .map(CategoryReadDto::fromEntity)
	                .toList();
	}

    // 부모 카테고리로 목록 조회
    @Transactional
    public List<CategoryReadDto> getAllCategoriesByParent(Long parentId) {
        List<Category> categories = categoryRepository.findAllByParent_CategoryId(parentId);
        return categories.stream()
                .map(CategoryReadDto::fromEntity)
                .toList();
    }
}
