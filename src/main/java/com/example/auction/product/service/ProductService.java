package com.example.auction.product.service;

import org.springframework.stereotype.Service;

import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Tag;
import com.example.auction.product.dto.ProductCreateDto;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.product.repository.TagRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
	

	private final TagRepository tagRepository;
	private final ProductRepository productRepository;
	
	public Product createProduct(ProductCreateDto dto) {
		Tag tag = tagRepository.findById(dto.getTagId())
				.orElseThrow(() -> new RuntimeException("태그 없음"));
		Product product = dto.toProduct();
		product.setTag(tag);
		
		return productRepository.save(product);
	}
}
