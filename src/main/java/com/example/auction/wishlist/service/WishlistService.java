package com.example.auction.wishlist.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.wishlist.domain.Wishlist;
import com.example.auction.wishlist.dto.WishlistCreateDto;
import com.example.auction.wishlist.repository.WishlistRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WishlistService {
	
	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final WishlistRepository wishlistRepository;
	
	// 테스트, 개선 필요
	@Transactional
	public Wishlist createWishlist(WishlistCreateDto dto) {
		User user = userRepository.findById(dto.getUserId())
				.orElseThrow(() -> new RuntimeException("유저 없음"));
		
		Product product = productRepository.findById(dto.getProductId())
				.orElseThrow(() -> new RuntimeException("상품 없음"));
		
		Wishlist wishlist = new Wishlist();
		wishlist.setProduct(product);
		wishlist.setUser(user);
		
		return wishlistRepository.save(wishlist);
	}

}
