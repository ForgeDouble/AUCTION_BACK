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
@Slf4j
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
    	log.info("ReadProduct called with id={}", productId);
        try {
			ProductReadDto productReadDto = productService.readProduct(productId);
			return ResponseEntity.ok(productReadDto);
		} catch (RuntimeException e) {
			log.error("런타임 예외 발생", e);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		} catch (Exception e) {
			log.error("알 수 없는 서버 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }
    
    /* 상품 목록 조회 */
    @GetMapping("/")
    public ResponseEntity<?> ReadAllProducts() {
        try {
            List<ProductReadAllDto> products = productService.readAllProducts();
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }
    
    // 상품 수정
    @PutMapping("/")
    public ResponseEntity<?> updateProduct(@ModelAttribute ProductUpdateDto productUpdateDto) {
        try {
            productService.updateProduct(productUpdateDto);
            return ResponseEntity.ok("상품정보 수정 완료");
        } catch (RuntimeException e) {
        	return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 내부 오류");
        }
    }
    
    // 상품 삭제
    @PutMapping("/delete")
    public ResponseEntity<?> deleteProduct(@ModelAttribute ProductDeleteDto productDeleteDto) {
        try {
            productService.deleteProduct(productDeleteDto);
            return ResponseEntity.ok("상품정보 삭제 완료");
        } catch (RuntimeException e) {
        	return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 내부 오류");
        }
    }
}

