package com.example.auction.product.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.example.auction.product.domain.Status;
import com.example.auction.product.dto.*;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ProductController {
	
	private final ProductService productService;
	    
	// 상품 생성
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createProduct(
            @ModelAttribute ProductCreateDto productCreateDto,
            @RequestPart("files") List<MultipartFile> files
    ) {
        productService.controllAuction(productCreateDto, files);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 생성 성공", null));
    }
    
    // 상품 단일 조회
    @GetMapping("/{productId}")
    public ResponseEntity<?> ReadProduct(@PathVariable("productId") Long productId) {
			ProductDetailDto dto = productService.readProduct(productId);
			return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 조회 성공", dto));
    }

    // 상품 단일 조회 (상품 수정 페이지)
    @GetMapping("/update/{productId}")
    public ResponseEntity<?> ReadEditProduct(@PathVariable("productId") Long productId) {
        ProductReadUpdateDto dto = productService.readUpdateProduct(productId);
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
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "NEWEST") String sortBy
    ) {
        // Pageable은 페이징만 처리
        Pageable pageable = PageRequest.of(page, size);

        // String을 Status enum으로 변환
        List<Status> statusEnums = null;

        if (statuses != null && !statuses.isEmpty()) {
            statusEnums = statuses.stream()
                    .map(Status::valueOf)
                    .collect(Collectors.toList());
        }

        Page<ProductListDto> products = productService.getProducts(
                categoryId,
                search,
                minPrice,
                maxPrice,
                statusEnums,
                sortBy,
                pageable
        );

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 조회 성공", products));
    }

    /* 로그인중인 유저의 상품 목록 조회 (마이페이지) */
    @GetMapping("/allByUser")
    public ResponseEntity<?> readAllProductsByUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<ProductWithBidDto> productPage = productService.readAllProductsByUser(page, size);

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 목록 조회 성공", productPage));
    }

    @GetMapping("/myPageProductUser")
    public ResponseEntity<?> myPageProductsByUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "NEWEST") String sortBy
    ) {
        List<Status> statusEnums = null;
        if (statuses != null && !statuses.isEmpty()) {
            statusEnums = statuses.stream()
                    .map(Status::valueOf)
                    .collect(Collectors.toList());
        }
        Page<ProductListDto> productPage =
                productService.myPageProductsByUser(page, size, search, statusEnums, sortBy);

        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 목록 조회 성공", productPage));
    }

    /* 로그인중인 유저의 찜한 상품 목록 조회 (마이페이지) */
    @GetMapping("/allByWishlist")
    public ResponseEntity<?> ReadAllProductsByWishlist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ProductWithBidDto> productPage = productService.readProductsByWishlist(page, size);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "찜한 상품 목록 조회 성공", productPage));
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
        log.info("dto ={}", productUpdateDto.toString());
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

    /* 메인페이지 - 입찰이 가장 많은 상위 3개 상품들 조회 */
    @GetMapping("/top3")
    public ResponseEntity<?> ReadTop3Product() {
        List<Top3ProductDto> top3ProductDtos = productService.readTop3Products();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 조회 성공", top3ProductDtos));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> productsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Status> statuses,
            @RequestParam(required = false) String sortBy
    ) {
        var result = productService.productsByTargetUser(userId, page, size, search, statuses, sortBy);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "유저 상품 조회 성공", result));
    }
}

