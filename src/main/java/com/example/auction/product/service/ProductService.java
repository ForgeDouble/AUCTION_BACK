package com.example.auction.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Tag;
import com.example.auction.product.dto.ProductCreateDto;
import com.example.auction.product.dto.ProductReadDto;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.product.repository.TagRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
	

	private final TagRepository tagRepository;
	private final ProductRepository productRepository;
	
	// 아이템 생성
	@Transactional
	public Product createProduct(ProductCreateDto dto) {
		Tag tag = tagRepository.findById(dto.getTagId())
				.orElseThrow(() -> new RuntimeException("태그 없음"));
		Product product = dto.toProduct();
		product.setTag(tag);
		
		return productRepository.save(product);
	}
	
	// 아이템 조회
	@Transactional
	public ProductReadDto readProduct(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("존재하지 않는 상품입니다."));
		return ProductReadDto.fromEntity(product);
	}
	
	
	
	
	// 아이템 수정
	
	// 아이템 삭제
}
