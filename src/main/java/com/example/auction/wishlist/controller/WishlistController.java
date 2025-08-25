package com.example.auction.wishlist.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.wishlist.dto.WishlistCreateDto;
import com.example.auction.wishlist.service.WishlistService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishlistController {
	
	private final WishlistService wishlistService;
	
//	위시리스트 생성
	@PostMapping("/create")
	public ResponseEntity<CommonResDto> createWishlist(@ModelAttribute WishlistCreateDto wishlistCreateDto) {
		wishlistService.createWishlist(wishlistCreateDto);
		return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "위시리스트 생성 성공", wishlistCreateDto));
	}
	
}
