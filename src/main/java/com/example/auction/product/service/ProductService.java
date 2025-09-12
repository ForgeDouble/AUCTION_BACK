package com.example.auction.product.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.dto.*;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.example.auction.wishlist.repository.WishlistRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.category.domain.Category;
import com.example.auction.category.repository.CategoryRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@Slf4j
public class ProductService {

    private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> bidRedisTemplate;
    private final RedisTemplate<String, String> bidStringRedisTemplate;

    public ProductService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Qualifier("bid") RedisTemplate<String, Object> bidRedisTemplate,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidStringRedisTemplate
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.bidRedisTemplate = bidRedisTemplate;
        this.bidStringRedisTemplate = bidStringRedisTemplate;
    }

    /* 상품 임시정지 / 정지 함수 */
	private void ensureCanMutateProducts(User user, String action) {
		if (Boolean.TRUE.equals(user.getViewOnly())) {
			throw new UnauthorizedAccessException("임시 제한(view-only) 상태라 " + action + "할 수 없습니다.");
		}
		if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
			String until = user.getSuspendedUntil().truncatedTo(ChronoUnit.SECONDS).toString().replace('T', ' ');
			throw new UnauthorizedAccessException("정지된 계정입니다. 해제 시각: " + until);
		}
	}

    // 아이템 생성
    @Transactional
    public Product createProduct(ProductCreateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

        ensureCanMutateProducts(user, "상품 등록");

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category"));

        Product product = dto.toProduct();
        product.setCategory(category);
        product.setUser(user);
        Product savedProduct = productRepository.save(product);

        String bidZSetKey = "product_bid_zset_" + savedProduct.getProductId();
        String bidHashKey = "product_bid_hash_" + savedProduct.getProductId();

        BidEvent bidEvent = BidEvent.builder()
                .userId(user.getUserId())
                .productId(savedProduct.getProductId())
                .bidAmount(savedProduct.getPrice())
                .isWinned(IsWinned.N)
                .build();

        try {
            String bidEventJson = objectMapper.writeValueAsString(bidEvent);
            String uuid = UUID.randomUUID().toString();

            // Lua 스크립트로 ZSET + Hash 초기값 세팅 (원자성 보장)
            String luaScript = """
            local zsetKey = KEYS[1]
            local hashKey = KEYS[2]
            local uuId = ARGV[1]
            local bidAmount = tonumber(ARGV[2])
            local bidEventJson = ARGV[3]

            -- ZSET에 초기값 없으면 세팅
            local exists = redis.call('ZCARD', zsetKey)
            if exists == 0 then
                local added = redis.call('ZADD', zsetKey, bidAmount, uuId)
                if added == 1 then
                    redis.call('HSET', hashKey, uuId, bidEventJson)
                    return 1
                else
                    return 2
                end
            else
                return 0
            end
            """;

            Long result = bidStringRedisTemplate.execute(
                    new DefaultRedisScript<>(luaScript, Long.class),
                    List.of(bidZSetKey, bidHashKey),
                    uuid,
                    String.valueOf(savedProduct.getPrice()),
                    bidEventJson
            );

            if (result == null || result == 0) {
                throw new RuntimeException("Redis 초기 입찰 세팅 실패 - 초기값이 존재합니다.");
            } else if (result == 2) {
                throw new RuntimeException("Redis 초기 입찰 세팅 실패 - ZSET 입력을 실패했습니다.");
            }


            log.info("Redis ZSET + Hash 초기 입찰가 세팅 완료 - ZSET Key: {}, Hash Key: {}, UUID: {}, 시작가: {}",
                    bidZSetKey, bidHashKey, uuid, savedProduct.getPrice());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("BidEvent JSON 변환 실패", e);
        }
        return savedProduct;
    }


    // DelYN.N 인것을 조회
	// 아이템 조회
	@Transactional(readOnly = true)
	public ProductReadDto readProduct(Long productId) {
		Product product = productRepository
				.findByProductIdAndDelYnAndBlocked(productId, DelYN.N, false)
				.orElseThrow(() -> new ResourceNotFoundException("Product"));
		return ProductReadDto.fromEntity(product);
	}
	
	// 아이템 목록 상세 조회
	@Transactional(readOnly = true)
	public List<ProductReadAllDto> readAllProducts() {
		return productRepository.findAll().stream()
				.filter(product -> product.getDelYn() == DelYN.N)
				.filter(product -> !Boolean.TRUE.equals(product.getBlocked()))
				.map(ProductReadAllDto::fromEntity)
				.collect(Collectors.toList());
	}
	
	// 아이템 수정
    // 권한 - 해당 유저, 관리자
	@Transactional
	public void updateProduct(ProductUpdateDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

		ensureCanMutateProducts(user, "상품 수정");

        Product product = productRepository.findById(dto.getProductId())
				.orElseThrow(() -> new ResourceNotFoundException("Product"));
		
	    Category category = categoryRepository.findById(dto.getCategoryId())
	        .orElseThrow(() -> new IllegalArgumentException("Category"));

        if(user.getAuthority() != Authority.ADMIN && !user.getUserId().equals(product.getUser().getUserId())) {
            throw new UnauthorizedAccessException("해당 상품을 수정할 권한이 없습니다.");
        }
		product.update(dto, category);
		productRepository.save(product);
	}
	
	
	// 아이템 소프트 삭제
    // 권한 - 해당 유저, 관리자
	@Transactional
	public void deleteProduct(Long productId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

		ensureCanMutateProducts(user, "상품 삭제");

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new ResourceNotFoundException("Product"));

        if(user.getAuthority() != Authority.ADMIN && !user.getUserId().equals(product.getUser().getUserId())) {
            throw new UnauthorizedAccessException("해당 상품을 삭제할 권한이 없습니다.");
        }

		product.softDelete();
		productRepository.save(product);
	}
}
