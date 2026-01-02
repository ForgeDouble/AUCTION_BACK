package com.example.auction.wishlist.controller;

import com.example.auction.wishlist.domain.Wishlist;
import com.example.auction.wishlist.dto.WishlistAllDto;
import org.checkerframework.checker.units.qual.C;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
	
    /* 위시리스트 생성 */
    @PreAuthorize("isAuthenticated()")
	@PostMapping("/create")
	public ResponseEntity<CommonResDto> createWishlist(@ModelAttribute WishlistCreateDto wishlistCreateDto) {
		wishlistService.createWishlist(wishlistCreateDto);
		return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "위시리스트 생성 성공", null));
	}

    /* 사용자의 위시리스트 목록 조회 */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/allByUser")
    public ResponseEntity<CommonResDto> getAllWishlist() {
        List<WishlistAllDto> wishlistAllDtos = wishlistService.getAllWishlist();
        return  ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "위시리스트 조회 성공", wishlistAllDtos));
    }

    /* 해당 상품이 접속중인 유저의 위시리스트인지 판별 */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/isWishlisted/{productId}")
    public ResponseEntity<CommonResDto> clasifyWishlist(@PathVariable("productId") Long productId) {
        Long result = wishlistService.getWishlistId(productId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "위시리스트 조회 성공", result));
    }


    /* 위시리스트 삭제 */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/delete/{wishlistId}")
    public ResponseEntity<CommonResDto> deleteWishlist(@PathVariable("wishlistId") Long wishlistId) {
        wishlistService.deleteWishlistById(wishlistId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "위시리스트 삭제 성공", null));
    }
}
