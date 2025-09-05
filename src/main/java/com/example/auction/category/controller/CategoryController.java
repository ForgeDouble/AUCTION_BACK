package com.example.auction.category.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auction.category.dto.CategoryCreateDto;
import com.example.auction.category.dto.CategoryReadDto;
import com.example.auction.category.service.CategoryService;
import com.example.auction.common.dto.CommonResDto;
import com.example.auction.product.dto.ProductReadDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {
	private final CategoryService categoryService;
	
	// 카테고리 생성
    @PostMapping("/create")
    public ResponseEntity<?> createCategory(@ModelAttribute CategoryCreateDto categoryCreateDto) {
    	categoryService.createCategory(categoryCreateDto);
    	return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "카테고리 생성 성공", categoryCreateDto));
    }
    
    // 카테고리 삭제
    // 관리자
    // 기본 delete
    @DeleteMapping("/delete/{categoryId}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "카테고리 삭제 성공", categoryId));
    }
    
    // 카테고리 단일 조회
    @GetMapping("/{categoryId}")
    public ResponseEntity<?> ReadCategory(@PathVariable("categoryId") Long categoryId) {
		CategoryReadDto categoryReadDto = categoryService.getCategory(categoryId);
		return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "카테고리 조회 성공", categoryReadDto));
    }
    
    // 전체 카테고리 목록 조회
    @GetMapping("/all")
    public ResponseEntity<?> getAllCategories() {
    	List<CategoryReadDto> categoryReadDtos = categoryService.getAllCategories();
    	return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "카테고리 목록 조회 성공", categoryReadDtos));
    }

    // 부모 카테고리로 목록 조회
    @GetMapping("/all/{parentId}")
    public ResponseEntity<?> getAllCategoriesByParentId(@PathVariable("parentId")  Long parentId) {
        List<CategoryReadDto> categoryReadDtos = categoryService.getAllCategoriesByParent(parentId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "카테고리 목록 조회 성공", categoryReadDtos));
    }
}
