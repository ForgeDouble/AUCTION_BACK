package com.example.auction.product.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.auction.product.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
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



    // 아이템 생성
	@Transactional
	public Product createProduct(ProductCreateDto dto) {
		Category category = categoryRepository.findById(dto.getCategoryId())
				.orElseThrow(() -> new RuntimeException("태그 없음"));
		Product product = dto.toProduct();
		product.setCategory(category);
		
		return productRepository.save(product);
	}
	
	// DelYN.N 인것을 조회
	// 아이템 조회
	@Transactional(readOnly = true)
	public ProductReadDto readProduct(Long productId) {
		Product product = productRepository
				.findByProductIdAndDelYnAndBlocked(productId, DelYN.N, false)
				.orElseThrow(() -> new RuntimeException("존재하지 않거나 비활성화된 상품입니다."));
		return ProductReadDto.fromEntity(product);
	}
	
	// 아이템 목록 조회
	@Transactional(readOnly = true)
	public List<ProductReadAllDto> readAllProducts() {
		return productRepository.findAll().stream()
				.filter(product -> product.getDelYn() == DelYN.N)
				.filter(product -> !Boolean.TRUE.equals(product.getBlocked()))
				.map(ProductReadAllDto::fromEntity)
				.collect(Collectors.toList());
	}
	
	// 아이템 수정
	@Transactional
	public void updateProduct(ProductUpdateDto dto) {
		Product product = productRepository.findById(dto.getProductId())
				.orElseThrow(() -> new RuntimeException("존재하지 않는 상품입니다."));
		
	    Category category = categoryRepository.findById(dto.getCategoryId())
	        .orElseThrow(() -> new IllegalArgumentException("태그 없음"));
	    
		product.update(dto, category);
		productRepository.save(product);
	}
	
	
	// 아이템 삭제
	@Transactional
	public void deleteProduct(ProductDeleteDto dto) {
		Product product = productRepository.findById(dto.getProductId())
				.orElseThrow(() -> new RuntimeException("존재하지 않는 상품입니다."));
		product.softDelete();
		productRepository.save(product);
	}
}
