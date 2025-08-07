package com.example.auction.product.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auction.product.dto.ProductCreateDto;
import com.example.auction.product.service.ProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
	
	private final ProductService productService;
	
    @GetMapping("/")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok("test ok");
    }
    
    @PostMapping("/")
    public ResponseEntity<?> createProduct(@ModelAttribute ProductCreateDto productCreateDto) {
        try {
			productService.createProduct(productCreateDto);
			return ResponseEntity.ok("상품 생성 완료");
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
		} catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }
}
