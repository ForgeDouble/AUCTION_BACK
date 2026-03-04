package com.example.auction.product.service;


import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.category.dto.CategoryBasicDto;
import com.example.auction.common.exception.AccountSuspendedException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.notification.service.AuctionNotificationService;
import com.example.auction.product.domain.Status;
import com.example.auction.product.dto.*;
import com.example.auction.product.repository.ProductImageRepository;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
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
    private final BidRepository bidRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> bidRedisTemplate;
    private final RedisTemplate<String, String> bidStringRedisTemplate;


    private final ProductImageRepository productImageRepository;
    private final ProductImageService productImageService;

    private final AuctionNotificationService auctionNotificationService;
    private final TaskScheduler taskScheduler;

    private static final int AUCTION_DURATION_HOURS = 24;

    public ProductService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            UserRepository userRepository, BidRepository bidRepository,
            ObjectMapper objectMapper,
            @Qualifier("bid") RedisTemplate<String, Object> bidRedisTemplate,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidStringRedisTemplate,

            ProductImageRepository productImageRepository, ProductImageService productImageService, AuctionNotificationService auctionNotificationService, TaskScheduler taskScheduler
    ) {

        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.bidRepository = bidRepository;
        this.objectMapper = objectMapper;
        this.bidRedisTemplate = bidRedisTemplate;
        this.bidStringRedisTemplate = bidStringRedisTemplate;
        this.productImageRepository = productImageRepository;

        this.productImageService = productImageService;
        this.auctionNotificationService = auctionNotificationService;
        this.taskScheduler = taskScheduler;
    }

    private static final String KEY_AUCTION_START = "auction:start:";
    // 시작 10/5분 전 키
    private static final String KEY_AUCTION_START_NOTIFY_10 = "auction:startNotify10:";
    private static final String KEY_AUCTION_START_NOTIFY_5 = "auction:startNotify5:";

    /* 상품 임시정지 / 정지 함수 */
	private void ensureCanMutateProducts(User user, String action) {
		if (Boolean.TRUE.equals(user.getViewOnly())) {
			throw new UnauthorizedAccessException("USER_TEMPORARY_RESTRICTED", "임시 제한(view-only) 상태라 " + action + "할 수 없습니다.");
		}
		if (user.getSuspendedUntil() != null && LocalDateTime.now().isBefore(user.getSuspendedUntil())) {
			String until = user.getSuspendedUntil().truncatedTo(ChronoUnit.SECONDS).toString().replace('T', ' ');
			throw new AccountSuspendedException("정지된 계정입니다.", until);
		}
	}


    @Transactional
    public void controllAuction(ProductCreateDto dto, List<MultipartFile> files) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다. email:" + email));

        /* 관리자 또는 고객센터는 접근 제한 */
        if(user.getAuthority().equals(Authority.ADMIN) | user.getAuthority().equals(Authority.INQUIRY)){
            log.warn("[NOT_ALLOWED] ADMIN 또는 INQUIRY의 접근 userId={}", user.getUserId());
            throw  new UnauthorizedAccessException("NOT_ALLOWED", "해당 계정은 접근할 권한이 없습니다.");
        }

        // 실제 DataBase에 Product 생성
        Product savedProduct = createProduct(dto, files, user);
        // 생성된 Product를 기준으로 입찰의 시작 가격을 bid테이블에 삽입
        // bid Redis data, 실제 DataBase bid data 삽입
        setFirstBid(savedProduct.getProductId());
    }

    // 아이템 생성
    @Transactional
    public Product createProduct(ProductCreateDto dto, List<MultipartFile> files, User user) {

        LocalDateTime now = LocalDateTime.now();

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("최소 1장의 이미지가 필요합니다.");
        }

        ensureCanMutateProducts(user, "상품 등록");

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new InternalErrorException("CATEGORY_NOT_FOUND","category를 조회하지 못했습니다. categoryId:" + dto.getCategoryId()));

        Product product = dto.toProduct();
        product.setCategory(category);
        product.setUser(user);
        Product savedProduct = productRepository.save(product);

        productImageService.uploadInitial(savedProduct.getProductId(), files);

        LocalDateTime startTime = savedProduct.getAuctionStartTime();
        LocalDateTime endTime = savedProduct.getAuctionEndTime();

        log.info("상품 생성 완료 - pid={}, startAt={}, endAt={}",
                savedProduct.getProductId(),
                startTime,
                endTime);


        // Redis에 경매 시작 타이머 등록 (5분 = 300초)
        long secondsUntilStart = Duration.between(now, startTime).getSeconds();
        if (secondsUntilStart <= 0) secondsUntilStart = 1; // 음수 방지 키워드 ( 서비스 저속 시 삭제)
        bidRedisTemplate.opsForValue().set(
                KEY_AUCTION_START + product.getProductId(),
                "1",
                Duration.ofSeconds(secondsUntilStart)
        );
        log.info("[ProductCreate] 시작 타이머 등록 pid={}, seconds={}",
                product.getProductId(), secondsUntilStart);

        // 시작 10분 전 / 5분 전 알림 키 등록 (판매자용)
        // 시작까지 10분 이상 남은 경우만 10분 전 키를 생성
        if (secondsUntilStart > 600) {
            bidRedisTemplate.opsForValue().set(
                    KEY_AUCTION_START_NOTIFY_10 + product.getProductId(),
                    "1",
                    Duration.ofSeconds(secondsUntilStart - 600)
            );
            log.info("[ProductCreate] 시작 10분 전 알림 타이머 등록 pid={}", product.getProductId());
        }

        // 시작까지 5분 이상 남은 경우만 5분 전 키를 생성
        if (secondsUntilStart > 300) {
            bidRedisTemplate.opsForValue().set(
                    KEY_AUCTION_START_NOTIFY_5 + product.getProductId(),
                    "1",
                    Duration.ofSeconds(secondsUntilStart - 300)
            );
            log.info("[ProductCreate] 시작 5분 전 알림 타이머 등록 pid={}", product.getProductId());
        }

        return savedProduct;
    }

    @Transactional
    public void setFirstBid(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new InternalErrorException("PRODUCT_NOT_FOUND", "상품을 조회하지 못했습니다. productId:" + productId));

        // 종료(판매) 여부 확인
        if (product.getStatus() != Status.READY) return;
        // 경매가 시작된 상품인지 확인
//        if (LocalDateTime.now().isBefore(product.getAuctionStartTime())) return;


        String bidZSetKey = "product_bid_zset_" + productId;
        String bidHashKey = "product_bid_hash_" + productId;
        String auctionTimeKey = "auction_end_time_" + productId;

        long auctionEndTimeMillis = product.getAuctionEndTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();


        try {
            String uuid = UUID.randomUUID().toString();
            BidEvent bidEvent = BidEvent.builder()
                    .userId(product.getUser().getUserId())
                    .userNickName(product.getUser().getNickname())
                    .productId(productId)
                    .bidAmount(product.getPrice())
                    .createdAt(product.getAuctionStartTime())
                    .isWinned(IsWinned.N)
                    .build();

            String bidEventJson = objectMapper.writeValueAsString(bidEvent);

//            경매 종료 시간 추가
//            long auctionEndTimeMillis = savedProduct.getCreatedAt().plusHours(AUCTION_DURATION_HOURS)
//                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

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
                    String.valueOf(product.getPrice()),
                    bidEventJson,
                    String.valueOf(auctionEndTimeMillis)
            );

            if (Objects.equals(result, 1L)) {
                log.info("Redis ZSET + Hash + 경매종료시간 초기 세팅 완료 - ProductId : {}, 입찰가 : {}", productId, product.getPrice());
//                auctionNotificationService.notifyAuctionStarted(productId);
            }
             else if (result == 2) {
                throw new InternalErrorException("REDIS_SET_ERROR", "Redis 초기 입찰 세팅 실패 - ZSET 입력을 실패했습니다.");
            }
        } catch (JsonProcessingException e) {
            log.warn("[Auction] baseline 직렬화 실패 pid={}", productId, e);
        }

//        auctionNotificationService.notifyAuctionStarted(productId);

        // 실제 보관용 bid 데이터
//        함수 반복 scheduler 수정되면 주석 푸시오
        Bid bid = new Bid();
        bid.setProduct(product);
        bid.setUser(product.getUser());
        bid.setBidAmount(product.getPrice());
        bid.setCreatedAt(product.getAuctionStartTime());
        bid.setIsWinned(IsWinned.N);

        bidRepository.save(bid);
    }

    /**
     * 경매 시작 (Status: READY → PROCESSING)
     */
    @Transactional
    public void startAuction(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다: " + productId));

        // 이미 시작된 경매는 무시
        if (product.getStatus() != Status.READY) {
            log.warn("[StartAuction] 이미 처리됨 pid={}, status={}", productId, product.getStatus());
            return;
        }

        // 상태 변경: READY → PROCESSING
        product.updateStatus(Status.PROCESSING);
        productRepository.save(product);

        // 알림(시작알림)
        auctionNotificationService.notifyAuctionStarted(productId);

        log.info("[StartAuction] 경매 시작 완료 pid={}, status={}", productId, product.getStatus());
    }


//    만료된 옥션들 처리
    // 현 로직중 해당 코드가 30초마다 돌아서 풀 확인 및 제어가능시 제어 필요
//    @Scheduled(fixedRate = 30000)
//    @Transactional(readOnly = true)
//    public void checkExpiredAuctions() {
//        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(AUCTION_DURATION_HOURS);
//
//        List<Product> expiredAuctions = productRepository
//                .findBySellYNAndCreatedAtBeforeOrderByCreatedAtAsc(SellYN.N, cutoffTime);
//
//        if (expiredAuctions.isEmpty()) {
//            return;
//        }
//
//        log.info("만료된 경매 발견 - 처리 대상: {}개", expiredAuctions.size());
//
//        // 배치로 처리 (대량 데이터 대비)
//
//        // 내부 컬럼 사용으로 SPRING AOP 정책 위반 -> 자기호출은  @Transaction 사용불가
//        // 이려면 checkExpiredAuctions() -> @Transactional 으로 인해서 MANAUAL 으로 바뀜 -> 여기서 호출된 endAuction() -> 트랜잭션 적용 불가 상태
//        // -> setSell / setIsWinned 커밋 flush 되지 않음 -> 반영이 안되거나 일부만 반영되는 원자성 보장 불가 문제 발생
//        // 해결방안 : endAuction을 별도 service 처리(@Bean 분리) 그 메서드에 @Transaction 처리를 하고 그 걸 불러와서 checkExpiredAuctions() 에서 호출
//        expiredAuctions.parallelStream()
//            .forEach(this::endAuctionSafely);
//    }

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
        Long pid = product.getProductId();
        try {
            Product currentProduct = productRepository.findById(pid)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            // 이미 종료된 건이면 중복 종료 방지
            if (currentProduct.getStatus() != Status.PROCESSING) {
                log.info("[Auction] 이미 종료된 상품 pid={}", pid);
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime auctionEndTime = currentProduct.getAuctionEndTime();

            // 스케줄러 주기(예: 1초)를 고려한 허용 범위
            // 종료 시간 1초 전부터 종료 처리 가능
            final int SCHEDULER_GRACE_SECONDS = 1;
            LocalDateTime allowedEndTime = auctionEndTime.minusSeconds(SCHEDULER_GRACE_SECONDS);

            if (now.isBefore(allowedEndTime)) {
                log.info("조기 종료 방지 - pid={}, now={}, allowedEnd={}, actualEnd={}",
                        pid, now, allowedEndTime, auctionEndTime);
                return;
            }

            log.info("경매 종료 처리 시작 - pid={}, scheduledEnd={}, actualProcessTime={}, diff={}ms",
                    pid, auctionEndTime, now,
                    ChronoUnit.MILLIS.between(auctionEndTime, now));

            // 2. Redis에서 최고 입찰자 확인
            String bidZSetKey = "product_bid_zset_" + pid;
            String bidHashKey = "product_bid_hash_" + pid;

            Long zcount = bidStringRedisTemplate.opsForZSet().size(bidZSetKey);
            if (zcount == null) zcount = 0L;

            // 최고 입찰가 조회 (ZSET에서 가장 높은 스코어 1개)
            Set<String> winners = bidStringRedisTemplate.opsForZSet().reverseRange(bidZSetKey, 0, 0);

            BidEvent winnerBid = null;
            String winnerUuid = null;

            if (winners != null && !winners.isEmpty()) {
                winnerUuid = winners.iterator().next();
                Object raw = bidStringRedisTemplate.opsForHash().get(bidHashKey, winnerUuid);
                if (raw != null) {
                    try {
                        winnerBid = objectMapper.readValue(raw.toString(), BidEvent.class);
                    } catch (Exception parseEx) {
                        log.warn("[Auction] 낙찰 이벤트 파싱 실패 pid={}, uuid={}, err={}", pid, winnerUuid, parseEx.toString());
                    }
                }
            }

            // "실제 낙찰" 판단:
            // - 엔트리가 2개 이상(zcount >= 2) => 베이스라인 외 실제 입찰 존재
            // - winnerBid가 있고 userId가 null 아님
            boolean hasRealWinner = (zcount >= 2) && (winnerBid != null) && (winnerBid.getUserId() != null);




            if (hasRealWinner) {
                // 1. 상품 상태를 Status.SELLED 변경
                currentProduct.updateStatus(Status.SELLED);
                productRepository.save(currentProduct);
                log.info("경매 종료 - ProductId: {}, 낙찰자: {}, 낙찰가: {}",
                        pid, winnerBid.getUserNickName(), winnerBid.getBidAmount());
                try {
                    Bid winningDbBid = bidRepository.findBidByUuid(winnerUuid).orElse(null);

                    if (winningDbBid != null) {
                        winningDbBid.setIsWinned(IsWinned.Y);
                        bidRepository.save(winningDbBid);
                        log.info("낙찰 처리 완료 - bidId={}, uuid={}",
                                winningDbBid.getBidId(), winnerUuid);
                    } else {
                        log.warn("낙찰자의 Bid 레코드를 DB에서 찾을 수 없음 - uuid={}, " +
                                "RabbitMQ 처리 지연 가능성", winnerUuid);
                    }

                    // 경매 종료 알림
                    auctionNotificationService.notifyAuctionEndedWithWinner(
                            currentProduct.getProductId(),
                            winnerBid,
                            currentProduct.getProductName()
                    );
                    log.info("경매 종료 알림");
                } catch (Exception ex) {
                    log.warn("[AuctionNotify] 낙찰자 알림 실패 productId={}, winnerUserId={}",
                            pid, winnerBid.getUserId(), ex);
                }
            } else {
                currentProduct.updateStatus(Status.NOTSELLED);
                productRepository.save(currentProduct);
                log.info("경매 종료 - ProductId: {}, 입찰자 없음(또는 기본가만 존재)", pid);
                try {
                    auctionNotificationService.notifyAuctionEndedNoWinner(
                            currentProduct.getProductId(), currentProduct.getProductName()
                    );
                } catch (Exception ex) {
                    log.warn("[AuctionNotify] '입찰자 없음' 알림 실패 productId={}", pid, ex);
                }
            }

        } catch (Exception e) {
            log.error("경매 종료 처리 중 오류 발생 - ProductId: {}", product.getProductId(), e);
        } finally {
            // 안전 차원에서 Redis 키 정리 (tick에서도 지우지만 중복 삭제 무해)
            try {
                String zsetKey = "product_bid_zset_" + pid;
                String hashKey = "product_bid_hash_" + pid;
                String timeKey = "auction_end_time_" + pid;
                bidStringRedisTemplate.delete(zsetKey);
                bidStringRedisTemplate.delete(hashKey);
                bidStringRedisTemplate.delete(timeKey);
            } catch (Exception ignore) { }
        }
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다 productId: " + productId));
    }


    // DelYN.N 인것을 조회
	// 아이템 상세 조회
    @Transactional(readOnly = true)
    public ProductDetailDto readProduct(Long productId) {
        Product product = productRepository
                .findByProductIdAndDelYnAndBlocked(productId, DelYN.N, false)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다 productId: " + productId));

        ProductDetailDto dto = ProductDetailDto.fromEntity(product);

        List<ProductImageDto> images = productImageRepository
                .findByProduct_ProductIdOrderByPositionAsc(productId)
                .stream()
                .map(ProductImageDto::from)
                .toList();

        dto.setImages(images);
        return dto;
    }

    // DelYN.N 인것을 조회
    // 아이템 상세 조회
    @Transactional(readOnly = true)
    public ProductReadUpdateDto readUpdateProduct(Long productId) {
        Product product = productRepository
                .findByProductIdAndDelYnAndBlocked(productId, DelYN.N, false)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));

        ProductReadUpdateDto dto = ProductReadUpdateDto.fromEntity(product);

        List<ProductImageDto> images = productImageRepository
                .findByProduct_ProductIdOrderByPositionAsc(productId)
                .stream()
                .map(ProductImageDto::from)
                .toList();

        dto.setImages(images);
        return dto;
    }
	
	// 아이템 목록 조회
//    @Transactional(readOnly = true)
//    public List<ProductListDto> readAllProducts() {
//        return productRepository.findAll().stream()
//                .filter(p -> p.getDelYn() == DelYN.N)
//                .filter(p -> !Boolean.TRUE.equals(p.getBlocked()))
//                .map(p -> {
//                    ProductListDto dto = ProductListDto.fromEntity(p);
//
//                    String previewUrl = productImageRepository
//                            .findByProduct_ProductIdOrderByPositionAsc(p.getProductId())
//                            .stream()
//                            .findFirst()
//                            .map(ProductImage::getUrl)
//                            .orElse(null);
//
//                    dto.setPreviewImageUrl(previewUrl);
//                    return dto;
//                })
//                .collect(Collectors.toList());
//    }

    @Transactional(readOnly = true)
    public Page<ProductListDto> getProducts(
            Long categoryId,
            String search,
            Long minPrice,
            Long maxPrice,
            List<Status> statuses,
            String sortBy,
            Pageable pageable
    ) {
        // 1. 카테고리 ID 리스트 생성 (부모 선택 시 모든 자식 포함)
        List<Long> categoryIds = null;

        if (categoryId != null && categoryId != 0) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new InternalErrorException("DATA_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
            categoryIds = getAllChildCategoryIds(category);
        }

        // 2. 상품 조회
        Page<ProductListDto> page = productRepository.findActiveProducts(
                categoryIds,
                search,
                minPrice,
                maxPrice,
                statuses,
                sortBy,
                pageable
        );

        // 3. 비어있으면 바로 반환
        if (page.isEmpty()) {
            return page;
        }

        // 4. 카테고리 path 설정
        List<ProductListDto> dtos = page.getContent();

        // categoryId 추출
        Set<Long> productCategoryIds = dtos.stream()
                .map(ProductListDto::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!productCategoryIds.isEmpty()) {
            // Category 조회 (batch fetch로 parent들도 효율적으로 조회됨)
            Map<Long, Category> categoryMap = categoryRepository
                    .findAllById(productCategoryIds)
                    .stream()
                    .collect(Collectors.toMap(Category::getCategoryId, c -> c));

            // path 설정
            dtos.forEach(dto -> {
                if (dto.getCategoryId() != null) {
                    Category cat = categoryMap.get(dto.getCategoryId());
                    if (cat != null) {
                        List<CategoryBasicDto> path = cat.getPath().stream()
                                .map(CategoryBasicDto::fromEntity)
                                .collect(Collectors.toList());
                        dto.setPath(path);
                    }
                }
            });
        }

        return page;
    }

    // 재귀적으로 모든 하위 카테고리 ID 수집
    private List<Long> getAllChildCategoryIds(Category category) {
        List<Long> ids = new ArrayList<>();
        ids.add(category.getCategoryId());

        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            for (Category child : category.getChildren()) {
                ids.addAll(getAllChildCategoryIds(child));
            }
        }

        return ids;
    }

    // 마이페이지 아이템 목록 조회
    @Transactional(readOnly = true)
    public Page<ProductWithBidDto> readAllProductsByUser(int page, int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return productRepository.findAllByUserEmailWithBidInfo(email, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductListDto> myPageProductsByUser(
            int page,
            int size,
            String search,
            List<Status> statuses,
            String sortBy
    ) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Pageable pageable = PageRequest.of(page, size);

        String normalizedSort = normalizeSort(sortBy);
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        List<Status> normalizedStatuses =
                (statuses == null || statuses.isEmpty()) ? null : statuses;

        return productRepository.findMyProducts(
                email,
                normalizedSearch,
                normalizedStatuses,
                normalizedSort,
                pageable
        );
    }

    private String normalizeSort(String sortBy) {
        if (sortBy == null) return "NEWEST";
        return switch (sortBy) {
            case "ENDING_SOON" -> "ENDING_SOON";
            case "MOST_BIDS" -> "MOST_BIDS";
            case "HIGHEST_BID" -> "HIGHEST_BID";
            case "PRICE_DESC" -> "HIGHEST_BID";
            case "NEWEST" -> "NEWEST";
            default -> "NEWEST";
        };
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
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다. email:" + email));

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new InternalErrorException("PRODUCT_NOT_FOUND", "상품을 조회하지 못했습니다. productId:" + dto.getProductId()));
        Category category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new InternalErrorException("CATEGORY_NOT_FOUND", "카테고리를 조회하지 못했습니다. categoryId:" + dto.getCategoryId()));
        }
        if (user.getAuthority() != Authority.ADMIN && !user.getUserId().equals(product.getUser().getUserId())) {
            throw new UnauthorizedAccessException("NOT_ALLOWED", "해당 상품을 수정할 권한이 없습니다.");
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
                .orElseThrow(() -> new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다. email:" + email));

		ensureCanMutateProducts(user, "상품 삭제");

		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new InternalErrorException("PRODUCT_NOT_FOUND", "유효하지 않은 상품입니다. productId:" + productId));

        if(user.getAuthority() != Authority.ADMIN && !user.getUserId().equals(product.getUser().getUserId())) {
            throw new UnauthorizedAccessException("해당 상품을 삭제할 권한이 없습니다.");
        }

		product.softDelete();
		productRepository.save(product);
	}

    /* 마이페이지 - 찜한 목록들 조회 */
    @Transactional(readOnly = true)
    public Page<ProductWithBidDto> readProductsByWishlist(int page, int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return productRepository.findWishlistByUserEmailWithBidInfo(email, pageable);
    }

    /* 메인페이지 - 입찰이 가장 많은 상위 3개 상품들 조회 */
    @Transactional(readOnly = true)
    public List<Top3ProductDto> readTop3Products() {
        return productRepository.findTop3ByBidCount();
    }

    @Transactional(readOnly = true)
    public Page<ProductListDto> productsByTargetUser(Long userId, int page, int size, String search, List<Status> statuses, String sortBy) {
        User target = userRepository.findById(userId)
                .filter(u -> u.getDelYn() == DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("User"));

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));

        String normalizedSort = normalizeSort(sortBy);
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        List<Status> normalizedStatuses = (statuses == null || statuses.isEmpty()) ? null : statuses;

        return productRepository.findMyProducts(
                target.getEmail(),
                normalizedSearch,
                normalizedStatuses,
                normalizedSort,
                pageable
        );

    }
}
