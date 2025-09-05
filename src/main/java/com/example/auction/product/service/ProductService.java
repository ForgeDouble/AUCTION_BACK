package com.example.auction.product.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.dto.*;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.wishlist.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.category.domain.Category;
import com.example.auction.category.repository.CategoryRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
    private final UserRepository userRepository;

	/* 상품 임시정지 / 정지 함수 */
	private void ensureCanMutateProducts(User user, String action) {
		if (Boolean.TRUE.equals(user.getViewOnly())) {
			throw new UnauthorizedAccessException("임시 제한(view-only) 상태라 " + action + "할 수 없습니다.");
		}
		if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
			String until = user.getSuspendedUntil().truncatedTo(ChronoUnit.SECONDS).toString().replace('T', ' ');
			throw new UnauthorizedAccessException("정지된 계정입니다. 해제 시각: " + until);
		}
	}

    // 아이템 생성
	@Transactional
	public Product createProduct(ProductCreateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

		ensureCanMutateProducts(user, "상품 등록");

		Category category = categoryRepository.findById(dto.getCategoryId())
				.orElseThrow(() -> new ResourceNotFoundException("Category"));

        Product product = dto.toProduct();

		product.setCategory(category);
        product.setUser(user);
		
		return productRepository.save(product);
	}
	
	// DelYN.N 인것을 조회
	// 아이템 조회
	@Transactional(readOnly = true)
	public ProductReadDto readProduct(Long productId) {
		Product product = productRepository
				.findByProductIdAndDelYnAndBlocked(productId, DelYN.N, false)
				.orElseThrow(() -> new ResourceNotFoundException("Product"));
		return ProductReadDto.fromEntity(product);
	}
	
	// 아이템 목록 상세 조회
	@Transactional(readOnly = true)
	public List<ProductReadAllDto> readAllProducts() {
		return productRepository.findAll().stream()
				.filter(product -> product.getDelYn() == DelYN.N)
				.filter(product -> !Boolean.TRUE.equals(product.getBlocked()))
				.map(ProductReadAllDto::fromEntity)
				.collect(Collectors.toList());
	}
	
	// 아이템 수정
    // 권한 - 해당 유저, 관리자
	@Transactional
	public void updateProduct(ProductUpdateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

		ensureCanMutateProducts(user, "상품 수정");

        Product product = productRepository.findById(dto.getProductId())
				.orElseThrow(() -> new ResourceNotFoundException("Product"));
		
	    Category category = categoryRepository.findById(dto.getCategoryId())
	        .orElseThrow(() -> new IllegalArgumentException("Category"));

        if(user.getAuthority() != Authority.ADMIN && !user.getUserId().equals(product.getUser().getUserId())) {
            throw new UnauthorizedAccessException("해당 상품을 수정할 권한이 없습니다.");
        }
		product.update(dto, category);
		productRepository.save(product);
	}
	
	
	// 아이템 소프트 삭제
    // 권한 - 해당 유저, 관리자
	@Transactional
	public void deleteProduct(Long productId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

		ensureCanMutateProducts(user, "상품 삭제");

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new ResourceNotFoundException("Product"));

        if(user.getAuthority() != Authority.ADMIN && !user.getUserId().equals(product.getUser().getUserId())) {
            throw new UnauthorizedAccessException("해당 상품을 삭제할 권한이 없습니다.");
        }

		product.softDelete();
		productRepository.save(product);
	}
}
