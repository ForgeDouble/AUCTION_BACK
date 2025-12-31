package com.example.auction.category.service;

import java.util.*;
import java.util.stream.Collectors;

import com.example.auction.category.dto.CategoryReadWithChildrenDto;
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
    // 카테고리 깊이
    private static final int MAX_DEPTH = 3;
	
	// 카테고리 생성
    // 관리자
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

    // 카테고리 삭제
    // 관리자
	@Transactional
	public void deleteCategory(Long categoryId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

        if (user.getAuthority() != Authority.ADMIN) {
            throw new UnauthorizedAccessException("관리자 외 권한이 없습니다.");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category"));

        // 연관된 product null 처리
        for (Product product : category.getProducts()) {
            product.setCategory(null);
        }

        for (Category child : category.getChildren()) {
            child.setParent(null);
        }

        categoryRepository.delete(category);
	}
	
	// 카테고리 단일 조회
    // 사용 안할 수 도 있음
	@Transactional
	public CategoryReadDto getCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category"));
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

    // 부모 id가 null인 카테고리 목록 조회
    @Transactional
    public List<CategoryReadDto> getNullParentCategories() {
        List<Category> categories = categoryRepository.findAllByParent_CategoryId(null);
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

    /* 카테고리 경로 조회 */
    @Transactional(readOnly = true)
    public String getFullPathDisplay(Long categoryId) {
        Category cur = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category"));

        Set<Long> visited = new HashSet<>();
        List<String> names = new ArrayList<>();
        while (cur != null) {
            if (!visited.add(cur.getCategoryId())) break;
            names.add(cur.getCategoryName());
            cur = cur.getParent();
        }
        Collections.reverse(names);
        return String.join(" > ", names);
    }

    /* 재귀적으로 자식 카테고리의 상품 개수를 포함한 총 개수 계산 */
    private long calculateTotalProductCount(
            Category category,
            Map<Long, Long> directCountMap,
            Map<Long, Long> totalCountMap) {

        /* 이미 계산된 경우 재사용 */
        if (totalCountMap.containsKey(category.getCategoryId())) {
            return totalCountMap.get(category.getCategoryId());
        }

        /* 현재 카테고리의 직접 상품 개수 */
        long totalCount = directCountMap.getOrDefault(category.getCategoryId(), 0L);

        /* 자식 카테고리들의 상품 개수를 재귀적으로 합산 */
        if (category.getChildren() != null) {
            for (Category child : category.getChildren()) {
                totalCount += calculateTotalProductCount(child, directCountMap, totalCountMap);
            }
        }

        /* 계산 결과 저장 */
        totalCountMap.put(category.getCategoryId(), totalCount);

        return totalCount;
    }

    /* 전체 카테고리 트리 (자식 포함) */
    @Transactional(readOnly = true)
    public List<CategoryReadWithChildrenDto> getAllCategoriesWithChildren() {
        /* 1. 카테고리 트리 조회 */
        List<Category> parentCategories = categoryRepository.findAllParentCategoriesWithChildren();

        /* 2. 각 카테고리의 직접 상품 개수를 Map으로 저장 */
        Map<Long, Long> directProductCountMap = categoryRepository.countProductsByCategory().stream()
                .collect(Collectors.toMap(
                        arr -> (Long) arr[0],
                        arr -> (Long) arr[1]
                ));

        /* 3. 자식 포함 상품 개수를 계산할 Map */
        Map<Long, Long> totalProductCountMap = new HashMap<>();

        /* 4. 모든 카테고리에 대해 재귀적으로 총 상품 개수 계산 */
        for (Category category : parentCategories) {
            calculateTotalProductCount(category, directProductCountMap, totalProductCountMap);
        }

        /* 5. DTO 변환 */
        return parentCategories.stream()
                .map(category -> CategoryReadWithChildrenDto.fromWithChildren(
                        category, 0, MAX_DEPTH, totalProductCountMap))
                .collect(Collectors.toList());
    }
}
