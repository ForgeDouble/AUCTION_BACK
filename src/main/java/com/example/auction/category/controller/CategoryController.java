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
	
	// 태그 생성
    @PostMapping("/")
    public ResponseEntity<?> createTag(@ModelAttribute CategoryCreateDto tagCreateDto) {
    	categoryService.createCategory(tagCreateDto);
    	return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "태그 생성 성공", tagCreateDto));
    }
    
    //태그 삭제
//    @DeleteMapping("/")
//    public ResponseEntity<?> deleteTag() {
//    	
//    }
    
    // 태그 단일 조회
    @GetMapping("/{tagId}")
    public ResponseEntity<?> ReadTag(@PathVariable("tagId") Long tagId) {
		CategoryReadDto tagReadDto = categoryService.getCategory(tagId);
		return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "태그 조회 성공", tagReadDto));
    }
    
    // 태그 목록 조회
    @GetMapping("/")
    public ResponseEntity<?> getAllTags() {
    	List<CategoryReadDto> tagReadDtos = categoryService.getAllCategories();
    	return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "태그 목록 조회 성공", tagReadDtos));
    }
    
}
