package com.example.auction.wishlist.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.wishlist.dto.WishlistAllDto;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class WishlistService {
	
	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final WishlistRepository wishlistRepository;

//  위시리스트 생성
	@Transactional
	public Wishlist createWishlist(WishlistCreateDto dto) {
        System.out.println(dto.getProductId());
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

		Product product = productRepository.findByProductIdAndDelYn(dto.getProductId(), DelYN.N)
				.orElseThrow(() -> new ResourceNotFoundException("Product"));

        if(product.getUser().getEmail().equals(email)) {
            throw new IllegalStateException("본인 상품에는 위시리스트를 추가 할 수 없습니다.");
        }

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

    //* 해당 게시물이 접속중인 유저의 위시리스트 ID를 반환하는 함수 */
    @Transactional(readOnly = true)
    public Long getWishlistId(Long productId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

        // Product 존재 여부 확인 (필요한 경우)
        if (!productRepository.existsById(productId)) {
            log.warn("[PRODUCT_NOT_FOUND] 존재하지 않는 경매 productId={}", productId);
            throw new ResourceNotFoundException("해당 경매를 찾을 수 없습니다.");
        }

        // DB에서 wishlistId 조회 (없으면 null 반환)
        Long result = wishlistRepository.findWishlistIdByUser_UserIdAndProduct_ProductId(
                user.getUserId(),
                productId
        ).orElse(null);

        return result;
    }

//  사용자의 위시리스트 삭제
    @Transactional
    public void deleteWishlistById(Long wishlistId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> {
                    log.warn("[USER_NOT_FOUND] 존재하지 않는 유저 email={}", email);
                    throw new ResourceNotFoundException("해당 유저를 찾을 수 없습니다.");
                }
        );

        Wishlist wishlist = wishlistRepository.findByWishlistId(wishlistId)
                .orElseThrow(() -> {
                    log.warn("[WISHLIST_NOT_FOUND] 존재하지 않는 위시리스트 wishlistId={}", wishlistId);
                    throw new ResourceNotFoundException("해당 위시리스트를 찾을 수 없습니다.");
                });

        if (!wishlist.getUser().getUserId().equals(user.getUserId())) {
            log.warn("[NOT_ALLOWED] 위시리시트의 유저와 다른 유저 wishlistUserId={} userId={}", wishlist.getUser().getUserId(), user.getUserId() );
            throw new UnauthorizedAccessException("해당 위시리스트를 삭제할 권한이 없습니다.");
        }
        wishlistRepository.delete(wishlist);
    }
}
