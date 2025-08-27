package com.example.auction.wishlist.controller;

import com.example.auction.wishlist.dto.WishlistAllDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.auction.common.dto.CommonResDto;
import com.example.auction.wishlist.dto.WishlistCreateDto;
import com.example.auction.wishlist.service.WishlistService;

import lombok.RequiredArgsConstructor;

import java.util.List;

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

//  위시리스트 목록 조회
    @GetMapping("/all")
    public ResponseEntity<CommonResDto> getAllWishlist() {
        List<WishlistAllDto> wishlistAllDtos = wishlistService.getAllWishlist();
        return  ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "위시리스트 조회 성공", wishlistAllDtos));
    }



//  위시리스트 삭제

	
}
