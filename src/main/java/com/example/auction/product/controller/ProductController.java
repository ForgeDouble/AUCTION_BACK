package com.example.auction.product.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.product.dto.ProductCreateDto;
import com.example.auction.product.dto.ProductDeleteDto;
import com.example.auction.product.dto.ProductReadAllDto;
import com.example.auction.product.dto.ProductReadDto;
import com.example.auction.product.dto.ProductUpdateDto;
import com.example.auction.product.service.ProductService;
import com.example.auction.user.dto.UserDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
	
	private final ProductService productService;
	    
	// 상품 생성
    @PostMapping("/")
    public ResponseEntity<?> createProduct(@ModelAttribute ProductCreateDto productCreateDto) {
			productService.createProduct(productCreateDto);
			return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 생성 성공", productCreateDto));
    }
    
    // 상품 단일 조회
    @GetMapping("/{productId}")
    public ResponseEntity<?> ReadProduct(@PathVariable("productId") Long productId) {
			ProductReadDto productReadDto = productService.readProduct(productId);
			return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 조회 성공", productReadDto));
    }
    
    /* 상품 목록 조회 */
    @GetMapping("/")
    public ResponseEntity<?> ReadAllProducts() {
    	List<ProductReadAllDto> productReadAllDtos = productService.readAllProducts();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 목록 조회 성공", productReadAllDtos));
    }
    
    // 상품 수정
    @PutMapping("/")
    public ResponseEntity<?> updateProduct(@ModelAttribute ProductUpdateDto productUpdateDto) {
    	productService.updateProduct(productUpdateDto);
    	return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 정보 수정 성공", null));
    }
    
    // 상품 삭제
    @PutMapping("/delete")
    public ResponseEntity<?> deleteProduct(@ModelAttribute ProductDeleteDto productDeleteDto) {
    	productService.deleteProduct(productDeleteDto);
    	return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 정보 삭제 성공", null));
    }
}

