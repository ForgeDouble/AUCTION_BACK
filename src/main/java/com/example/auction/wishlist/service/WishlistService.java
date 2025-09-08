package com.example.auction.wishlist.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
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

//  위시리스트 생성
	@Transactional
	public Wishlist createWishlist(WishlistCreateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

		Product product = productRepository.findByProductIdAndDelYn(dto.getProductId(), DelYN.N)
				.orElseThrow(() -> new ResourceNotFoundException("Wishlist"));

        boolean exists = wishlistRepository.existsByUser_UserIdAndProduct_ProductId(user.getUserId(), product.getProductId());
        if (exists) {
            throw new IllegalStateException("이미 위시리스트에 있습니다.");
        }

		Wishlist wishlist = new Wishlist();
		wishlist.setProduct(product);
		wishlist.setUser(user);
		
		return wishlistRepository.save(wishlist);
	}

//  사용자의 위시리스트 목록 조회
    @Transactional(readOnly = true)
    public List<WishlistAllDto> getAllWishlist() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

        List<WishlistAllDto> wishlistAllDtos = wishlistRepository.findByUser_UserId(user.getUserId()).stream()
                .map(WishlistAllDto::fromEntity)
                .collect(Collectors.toList());
        return wishlistAllDtos;
    }

//  사용자의 위시리스트 삭제
    @Transactional
    public void deleteWishlistById(Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

        Wishlist wishlist = wishlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist", id));

        if (!wishlist.getUser().getUserId().equals(user.getUserId())) {
            throw new UnauthorizedAccessException("해당 위시리스트를 삭제할 권한이 없습니다.");
        }
        wishlistRepository.delete(wishlist);
    }
}
