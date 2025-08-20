package com.example.auction.product.controller;

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

import com.example.auction.product.dto.ProductReadDto;
import com.example.auction.product.dto.TagCreateDto;
import com.example.auction.product.dto.TagReadDto;
import com.example.auction.product.service.TagService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
public class TagController {
	private final TagService tagService;
	
	// 태그 생성
    @PostMapping("/")
    public ResponseEntity<?> createTag(@ModelAttribute TagCreateDto tagCreateDto) {
        try {
			tagService.createTag(tagCreateDto);
			return ResponseEntity.ok("태그 생성 완료");
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
		} catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }
    
    //태그 삭제
//    @DeleteMapping("/")
//    public ResponseEntity<?> deleteTag() {
//    	
//    }
    
    // 태그 단일 조회
    @GetMapping("/{tagId}")
    public ResponseEntity<?> ReadTag(@PathVariable("tagId") Long tagId) {
        try {
			TagReadDto tagReadDto = tagService.getTag(tagId);
			return ResponseEntity.ok(tagReadDto);
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		} catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }
    
    // 태그 목록 조회
    @GetMapping("/")
    public ResponseEntity<?> getAllTags() {
    	try {
    		List<TagReadDto> tagReadDtos = tagService.getAllTags();
    		return ResponseEntity.ok(tagReadDtos);
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
		}
    }
    
}
