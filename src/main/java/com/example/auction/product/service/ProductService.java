package com.example.auction.product.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.ProductImage;
import com.example.auction.product.domain.SellYN;
import com.example.auction.product.dto.*;
import com.example.auction.product.repository.ProductImageRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auction.category.domain.Category;
import com.example.auction.category.repository.CategoryRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class ProductService {

    private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> bidRedisTemplate;
    private final RedisTemplate<String, String> bidStringRedisTemplate;

    private final ProductImageRepository productImageRepository;
    private final ProductImageService productImageService;
    private final TaskScheduler taskScheduler;

    private static final int AUCTION_DURATION_HOURS = 24;

    public ProductService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Qualifier("bid") RedisTemplate<String, Object> bidRedisTemplate,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidStringRedisTemplate,
            ProductImageRepository productImageRepository, ProductImageService productImageService, TaskScheduler taskScheduler
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.bidRedisTemplate = bidRedisTemplate;
        this.bidStringRedisTemplate = bidStringRedisTemplate;
        this.productImageRepository = productImageRepository;

        this.productImageService = productImageService;
        this.taskScheduler = taskScheduler;
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
    public Product createProduct(ProductCreateDto dto, List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("최소 1장의 이미지가 필요합니다.");
        }

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

        productImageService.uploadInitial(savedProduct.getProductId(), files);

        // 경매 종료 스케줄링 추가
        if (savedProduct.getSellYN() == SellYN.N) { // 경매 상품인 경우만
            LocalDateTime endTime = savedProduct.getCreatedAt().plusHours(AUCTION_DURATION_HOURS);
            Instant endInstant = endTime.atZone(ZoneId.systemDefault()).toInstant(); // schedule() 함수에 맞게 변형

            taskScheduler.schedule(() -> {
                endAuction(savedProduct);
            }, endInstant);

            log.info("경매 자동 종료 스케줄링 등록 - ProductId: {}, 종료예정: {}",
                    savedProduct.getProductId(), endTime);
        }

        String bidZSetKey = "product_bid_zset_" + savedProduct.getProductId();
        String bidHashKey = "product_bid_hash_" + savedProduct.getProductId();
        String auctionTimeKey = "auction_end_time_" + savedProduct.getProductId();

        BidEvent bidEvent = BidEvent.builder()
                .userId(user.getUserId())
                .userName(user.getName())
                .productId(savedProduct.getProductId())
                .bidAmount(savedProduct.getPrice())
                .createdAt(LocalDateTime.now())
                .isWinned(IsWinned.N)
                .build();

        try {
            String bidEventJson = objectMapper.writeValueAsString(bidEvent);
            String uuid = UUID.randomUUID().toString();

//            경매 종료 시간 추가
            long auctionEndTimeMillis = savedProduct.getCreatedAt().plusHours(AUCTION_DURATION_HOURS)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // 테스트용 코드
//            long auctionEndTimeMillis = savedProduct.getCreatedAt().plusHours(0)
//                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // Lua 스크립트로 ZSET + Hash 초기값 세팅 (원자성 보장)
            String luaScript = """
        local zsetKey = KEYS[1]
        local hashKey = KEYS[2]
        local timeKey = KEYS[3]
        local uuId = ARGV[1]
        local bidAmount = tonumber(ARGV[2])
        local bidEventJson = ARGV[3]
        local auctionEndTime = ARGV[4]

        -- ZSET에 초기값 없으면 세팅
        local exists = redis.call('ZCARD', zsetKey)
        if exists == 0 then
            local added = redis.call('ZADD', zsetKey, bidAmount, uuId)
            if added == 1 then
                redis.call('HSET', hashKey, uuId, bidEventJson)
                -- 경매 종료 시간 저장 (24시간 + 1시간 여유분으로 TTL 설정)
                redis.call('SET', timeKey, auctionEndTime, 'EX', 90000)
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
                    List.of(bidZSetKey, bidHashKey, auctionTimeKey),
                    uuid,
                    String.valueOf(savedProduct.getPrice()),
                    bidEventJson,
                    String.valueOf(auctionEndTimeMillis)
            );

            if (result == null || result == 0) {
                throw new RuntimeException("Redis 초기 입찰 세팅 실패 - 초기값이 존재합니다.");
            } else if (result == 2) {
                throw new RuntimeException("Redis 초기 입찰 세팅 실패 - ZSET 입력을 실패했습니다.");
            }

            log.info("Redis ZSET + Hash + 경매종료시간 초기 세팅 완료 - ProductId: {}, 종료시간: {}, 시작가: {}",
                    savedProduct.getProductId(), auctionEndTimeMillis, savedProduct.getPrice());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("BidEvent JSON 변환 실패", e);
        }
        return savedProduct;
    }

//    만료된 옥션들 처리
    @Scheduled(fixedRate = 30000)
    @Transactional(readOnly = true)
    public void checkExpiredAuctions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(AUCTION_DURATION_HOURS);

        List<Product> expiredAuctions = productRepository
                .findBySellYNAndCreatedAtBeforeOrderByCreatedAtAsc(SellYN.N, cutoffTime);

        if (expiredAuctions.isEmpty()) {
            return;
        }

        log.info("만료된 경매 발견 - 처리 대상: {}개", expiredAuctions.size());

        // 배치로 처리 (대량 데이터 대비)

        // 내부 컬럼 사용으로 SPRING AOP 정책 위반 -> 자기호출은  @Transaction 사용불가
        // 이려면 checkExpiredAuctions() -> @Transactional 으로 인해서 MANAUAL 으로 바뀜 -> 여기서 호출된 endAuction() -> 트랜잭션 적용 불가 상태
        // -> setSell / setIsWinned 커밋 flush 되지 않음 -> 반영이 안되거나 일부만 반영되는 원자성 보장 불가 문제 발생
        // 해결방안 : endAuction을 별도 service 처리(@Bean 분리) 그 메서드에 @Transaction 처리를 하고 그 걸 불러와서 checkExpiredAuctions() 에서 호출
        expiredAuctions.parallelStream()
            .forEach(this::endAuctionSafely);
    }

    private void endAuctionSafely(Product product) {
        try {
            endAuction(product);
        } catch (Exception e) {
            log.error("개별 경매 종료 처리 실패 - ProductId: {}", product.getProductId(), e);
            // 한 건 실패가 전체를 막지 않도록
        }
    }

//    경매 낙찰 처리
    @Transactional
    public void endAuction(Product product) {
        try {

            Product currentProduct = productRepository.findById(product.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            // 1. 상품 상태를 SellYN.Y 변경
            currentProduct.setSellYN(SellYN.Y);

            //  DB의 Bid.isWinned를 Y로 바꾸지 않음
            // findByProduct_ProductIdAndIsWinned(..., Y)가 없어서 404(ResourceNotFoundException).

            // redis 에 정보를 받아올 때
            // 잔존 키 제거에 대한 코드가 부재
            // 의구심 : 우리 서비스가 입찰 취소 기능의 여부가 있나? (기능을 제공하나)
            // 요약 : 종료 시점에 ZSET/HASH/TIME 모두 정리가 들어가야 하고, 현재 코드는 그 부분이 빠져 있습니다.
            productRepository.save(currentProduct);

            // 2. Redis에서 최고 입찰자 확인
            String bidZSetKey = "product_bid_zset_" + currentProduct.getProductId();
            String bidHashKey = "product_bid_hash_" + currentProduct.getProductId();

            // 최고 입찰가 조회 (ZSET에서 가장 높은 스코어)
            Set<String> winners = bidStringRedisTemplate.opsForZSet()
                    .reverseRange(bidZSetKey, 0, 0); // 최고가 1개만

            if (winners != null && !winners.isEmpty()) {
                String winnerUuid = winners.iterator().next();
                String bidEventJson = bidStringRedisTemplate.opsForHash()
                        .get(bidHashKey, winnerUuid).toString();

                BidEvent winnerBid = objectMapper.readValue(bidEventJson, BidEvent.class);

                log.info("경매 종료 - ProductId: {}, 낙찰자: {}, 낙찰가: {}",
                        currentProduct.getProductId(), winnerBid.getUserName(), winnerBid.getBidAmount());

                // 3. 낙찰 처리 로직 (결제, 알림 등)
//                processWinningBid(product, winnerBid);
            } else {
                log.info("경매 종료 - ProductId: {}, 입찰자 없음", product.getProductId());
            }

        } catch (Exception e) {
            log.error("경매 종료 처리 중 오류 발생 - ProductId: {}", product.getProductId(), e);
        }
    }

    // DelYN.N 인것을 조회
	// 아이템 상세 조회
    @Transactional(readOnly = true)
    public ProductDetailDto readProduct(Long productId) {
        Product product = productRepository
                .findByProductIdAndDelYnAndBlocked(productId, DelYN.N, false)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));

        ProductDetailDto dto = ProductDetailDto.fromEntity(product);

        List<ProductImageDto> images = productImageRepository
                .findByProduct_ProductIdOrderByPositionAsc(productId)
                .stream()
                .map(ProductImageDto::from)
                .toList();

        dto.setImages(images);
        return dto;
    }
	
	// 아이템 목록 조회
    @Transactional(readOnly = true)
    public List<ProductListDto> readAllProducts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getDelYn() == DelYN.N)
                .filter(p -> !Boolean.TRUE.equals(p.getBlocked()))
                .map(p -> {
                    ProductListDto dto = ProductListDto.fromEntity(p);

                    String previewUrl = productImageRepository
                            .findByProduct_ProductIdOrderByPositionAsc(p.getProductId())
                            .stream()
                            .findFirst()
                            .map(ProductImage::getUrl)
                            .orElse(null);

                    dto.setPreviewImageUrl(previewUrl);
                    return dto;
                })
                .collect(Collectors.toList());
    }
	
	// 아이템 수정
    // 권한 - 해당 유저, 관리자

    @Transactional
    public void updateProduct(ProductUpdateDto dto,
                                       List<MultipartFile> addFiles,
                                       List<Long> deleteIds,
                                       List<Long> orderIds) {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인중인 User"));

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product"));
        Category category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category"));
        }
        if (user.getAuthority() != Authority.ADMIN && !user.getUserId().equals(product.getUser().getUserId())) {
            throw new UnauthorizedAccessException("해당 상품을 수정할 권한이 없습니다.");
        }
        ensureCanMutateProducts(user, "상품 수정");

        product.update(dto, category);
        productRepository.save(product);

        productImageService.applyOps(
                product.getProductId(),
                addFiles,
                deleteIds,
                orderIds
        );
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
