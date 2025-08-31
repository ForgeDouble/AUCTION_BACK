package com.example.auction.wishlist.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.wishlist.dto.WishlistAllDto;
import org.springframework.security.core.context.SecurityContextHolder;
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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistService {
	
	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final WishlistRepository wishlistRepository;

//    위시리스트 생성
	@Transactional
	public Wishlist createWishlist(WishlistCreateDto dto) {
		User user = userRepository.findByUserIdAndDelYn(dto.getUserId(), DelYN.N)
				.orElseThrow(() -> new RuntimeException("유저 없음"));
		
		Product product = productRepository.findByProductIdAndDelYn(dto.getProductId(), DelYN.N)
				.orElseThrow(() -> new RuntimeException("상품 없음"));
		
		Wishlist wishlist = new Wishlist();
		wishlist.setProduct(product);
		wishlist.setUser(user);
		
		return wishlistRepository.save(wishlist);
	}

//    위시리스트 목록 조회
    @Transactional(readOnly = true)
    public List<WishlistAllDto> getAllWishlist() {
          List<WishlistAllDto> wishlistAllDtos = wishlistRepository.findAll().stream()
                .map(WishlistAllDto::fromEntity)
                  .collect(Collectors.toList());
          return wishlistAllDtos;
    }

    @Transactional
    public void deleteWishlistById(Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("현재 로그인한 유저 정보를 찾을 수 없습니다."));

        Wishlist wishlist = wishlistRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("해당 위시리스트가 존재하지 않습니다. id=" + id));

        if (!wishlist.getUser().getUserId().equals(user.getUserId())) {
            throw new RuntimeException("해당 위시리스트를 삭제할 권한이 없습니다.");
        }

        wishlistRepository.delete(wishlist);
    }
}
