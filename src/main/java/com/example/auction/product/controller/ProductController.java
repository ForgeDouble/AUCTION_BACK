package com.example.auction.product.controller;

import java.util.List;

import com.example.auction.product.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
//    @GetMapping("/all")
//    public ResponseEntity<?> ReadAllProducts(
//            @PageableDefault(size = 18, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
//    	Page<ProductListDto> dto = productService.readAllProducts(pageable);
//        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 목록 조회 성공", dto));
//    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        // Sort 파라미터 파싱
        String[] sortParams = sort.split(",");
        Sort sortOrder = Sort.by(
                sortParams.length > 1 && sortParams[1].equals("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                sortParams[0]  // createdAt, price 등 (camelCase)
        );

        Pageable pageable = PageRequest.of(page, size, sortOrder);

        Page<ProductListDto> products = productService.getProducts(
                categoryId,
                search,
                minPrice,
                maxPrice,
                pageable
        );

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 조회 성공", products));
    }

    /* 로그인중인 유저의 상품 목록 조회 (마이페이지) */
    @GetMapping("/allByUser")
    public ResponseEntity<?> ReadAllProductsByUser() {
        List<ProductWithBidDto> dto = productService.readAllProductsByUser();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 목록 조회 성공", dto));
    }

    /* 로그인중인 유저의 찜한 상품 목록 조회 (마이페이지) */
    @GetMapping("/allByWishlist")
    public ResponseEntity<?> ReadAllProductsByWishlist() {
        List<ProductWithBidDto> dto = productService.readProductsByWishlist();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "찜한 상품 목록 조회 성공", dto));
    }
    
    // 상품 수정
    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProduct(
            @ModelAttribute ProductUpdateDto productUpdateDto,
            @RequestPart(value = "addFiles", required = false) List<MultipartFile> addFiles,
            @RequestParam(value = "deleteImageIds", required = false) List<Long> deleteIds,
            @RequestParam(value = "orderImageIds", required = false) List<Long> orderIds
    ) {
        productService.updateProduct(productUpdateDto, addFiles, deleteIds, orderIds);
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

