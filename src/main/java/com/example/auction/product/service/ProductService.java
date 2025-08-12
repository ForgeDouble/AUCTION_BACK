package com.example.auction.product.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Tag;
import com.example.auction.product.dto.ProductCreateDto;
import com.example.auction.product.dto.ProductReadAllDto;
import com.example.auction.product.dto.ProductReadDto;
import com.example.auction.product.dto.ProductUpdateDto;
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
	
	// DelYN.N 인것을 조회
	// 아이템 조회
	@Transactional(readOnly = true)
	public ProductReadDto readProduct(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new RuntimeException("존재하지 않는 상품입니다."));
		return ProductReadDto.fromEntity(product);
	}
	
	// 전체 아이템 조회
	@Transactional(readOnly = true)
	public List<ProductReadAllDto> readAllProducts() {
		return productRepository.findAll().stream()
				.filter(product -> product.getDelYn() == DelYN.N)
				.map(ProductReadAllDto::fromEntity)
				.collect(Collectors.toList());
	}
	
	// 아이템 수정
	@Transactional
	public void updateProduct(ProductUpdateDto dto) {
		Product product = productRepository.findById(dto.getProductId())
				.orElseThrow(() -> new RuntimeException("존재하지 않는 상품입니다."));
		
	    Tag tag = tagRepository.findById(dto.getTagId())
	        .orElseThrow(() -> new IllegalArgumentException("태그 없음"));
	    
		product.update(dto, tag);
		productRepository.save(product);
	}
	
	
	// 아이템 삭제
	
}
