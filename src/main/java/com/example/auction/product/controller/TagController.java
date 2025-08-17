package com.example.auction.product.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auction.product.dto.TagCreateDto;
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
    
    
}
