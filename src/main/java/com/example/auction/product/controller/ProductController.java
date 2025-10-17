package com.example.auction.product.controller;

import java.util.List;

import com.example.auction.product.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.product.service.ProductService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
	
	private final ProductService productService;
	    
	// 상품 생성
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createProduct(
            @ModelAttribute ProductCreateDto productCreateDto,
            @RequestPart("files") List<MultipartFile> files
    ) {
        productService.createProduct(productCreateDto, files);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 생성 성공", null));
    }
    
    // 상품 단일 조회
    @GetMapping("/{productId}")
    public ResponseEntity<?> ReadProduct(@PathVariable("productId") Long productId) {
			ProductDetailDto dto = productService.readProduct(productId);
			return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 조회 성공", dto));
    }
    
    /* 상품 목록 조회 */
    @GetMapping("/all")
    public ResponseEntity<?> ReadAllProducts() {
    	List<ProductListDto> dto = productService.readAllProducts();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 목록 조회 성공", dto));
    }
    
    // 상품 수정
    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProduct(@ModelAttribute ProductUpdateDto productUpdateDto,
                                           @RequestPart(value = "addFiles", required = false) List<MultipartFile> addFiles,
                                           @RequestPart(value = "replaceImageIds", required = false) List<Long> replaceIds,
                                           @RequestPart(value = "replaceFiles", required = false) List<MultipartFile> replaceFiles,
                                           @RequestPart(value = "deleteImageIds", required = false) List<Long> deleteIds,
                                           @RequestPart(value = "orderImageIds", required = false) List<Long> orderIds
    ) {
        productService.updateProduct(productUpdateDto, addFiles, replaceIds, replaceFiles, deleteIds, orderIds);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 정보 수정 성공", null));
    }
    
    // 상품 삭제
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/delete/{productId}")
    public ResponseEntity<?> deleteProduct(@PathVariable("productId") Long productId) {
    	productService.deleteProduct(productId);
    	return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 정보 삭제 성공", null));
    }


}

