//package com.example.auction.common.data;
//
//import com.example.auction.bid.domain.Bid;
//import com.example.auction.bid.domain.IsWinned;
//import com.example.auction.bid.dto.BidEvent;
//import com.example.auction.bid.repository.BidRepository;
//import com.example.auction.category.domain.Category;
//import com.example.auction.category.repository.CategoryRepository;
//import com.example.auction.common.domain.DelYN;
//import com.example.auction.product.domain.Product;
//import com.example.auction.product.domain.Status;
//import com.example.auction.product.repository.ProductRepository;
//import com.example.auction.user.domain.User;
//import com.example.auction.user.repository.UserRepository;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.script.DefaultRedisScript;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Duration;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.util.List;
//import java.util.UUID;
//
//@Slf4j
//@Component
//@Profile("seed-test-product")
//public class TestProductSeedRunner implements CommandLineRunner {
//
//    private static final String SELLER_EMAIL = "user3@auction.test";
//    private static final Long   CATEGORY_ID  = 1L;
//    private static final long AUCTION_DURATION_HOURS = 336L;
//    private static final long START_PRICE = 1_000L;
//
//    private final UserRepository     userRepository;
//    private final CategoryRepository categoryRepository;
//    private final ProductRepository  productRepository;
//    private final BidRepository      bidRepository;
//    private final ObjectMapper       objectMapper;
//    private final RedisTemplate<String, String> bidStringRedisTemplate;
//
//    public TestProductSeedRunner(
//            UserRepository userRepository,
//            CategoryRepository categoryRepository,
//            ProductRepository productRepository,
//            BidRepository bidRepository,
//            ObjectMapper objectMapper,
//            @Qualifier("bidPrice") RedisTemplate<String, String> bidStringRedisTemplate
//    ) {
//        this.userRepository = userRepository;
//        this.categoryRepository = categoryRepository;
//        this.productRepository = productRepository;
//        this.bidRepository = bidRepository;
//        this.objectMapper = objectMapper;
//        this.bidStringRedisTemplate = bidStringRedisTemplate;
//    }
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//
//        /* 1. seller 조회 */
//        User seller = userRepository.findByEmailAndDelYn(SELLER_EMAIL, DelYN.N)
//                .orElseThrow(() -> new IllegalStateException(
//                        "[SEED] seller 유저를 찾을 수 없습니다. email=" + SELLER_EMAIL));
//
//        /* 2. category 조회 */
//        Category category = categoryRepository.findById(CATEGORY_ID)
//                .orElseThrow(() -> new IllegalStateException(
//                        "[SEED] category를 찾을 수 없습니다. categoryId=" + CATEGORY_ID));
//
//        /* 3. 이미 seed 상품이 있으면 스킵 */
//        boolean alreadyExists = productRepository
//                .findAll()
//                .stream()
//                .anyMatch(p -> "[SEED] 성능테스트 경매상품".equals(p.getProductName()));
//
//        if (alreadyExists) {
//            log.info("[SEED] 테스트 상품이 이미 존재합니다. 스킵합니다.");
//            return;
//        }
//
//        /* 4. Product 생성 (PROCESSING 상태로 직접 세팅) */
//        LocalDateTime now     = LocalDateTime.now();
//        // createdAt 을 과거로 잡아 getAuctionStartTime() < now 가 되도록 함
//        LocalDateTime fakeCreatedAt = now.minusHours(1);
//
//        Product product = Product.builder()
//                .category(category)
//                .user(seller)
//                .productName("[SEED] 성능테스트 경매상품")
//                .productContent("JMeter 성능 테스트용 상품입니다. 삭제하지 마세요.")
//                .price(START_PRICE)
//                .status(Status.PROCESSING)
//                .blocked(false)
//                .build();
//
//        // createdAt 은 BaseTimeEntity 가 자동 세팅하므로,
//        // Redis 종료시간만 72시간 후로 직접 지정합니다.
//        Product savedProduct = productRepository.save(product);
//        Long productId = savedProduct.getProductId();
//        log.info("[SEED] Product DB 저장 완료 productId={}", productId);
//
//        /* 5. 경매 종료 시간 = 지금 + 72시간 */
//        LocalDateTime auctionEndTime   = now.plusHours(AUCTION_DURATION_HOURS);
//        long auctionEndTimeMillis = auctionEndTime
//                .atZone(ZoneId.systemDefault())
//                .toInstant()
//                .toEpochMilli();
//
//        /* 6. Redis ZSET + Hash + auction_end_time 초기 세팅 (Lua 원자성 보장) */
//        String bidZSetKey     = "product_bid_zset_"   + productId;
//        String bidHashKey     = "product_bid_hash_"   + productId;
//        String auctionTimeKey = "auction_end_time_"   + productId;
//
//        String uuid = UUID.randomUUID().toString();
//
//        BidEvent baseBidEvent = BidEvent.builder()
//                .uuid(uuid)
//                .userId(seller.getUserId())
//                .userNickName(seller.getNickname())
//                .productId(productId)
//                .bidAmount(START_PRICE)
//                .createdAt(now)
//                .isWinned(IsWinned.N)
//                .build();
//
//        String bidEventJson = objectMapper.writeValueAsString(baseBidEvent);
//
//        // TTL = 72시간 + 1시간 여유 = 73시간 = 262800초
//        long ttlSeconds = Duration.ofHours(AUCTION_DURATION_HOURS + 1).getSeconds();
//
//        String luaScript = """
//                local zsetKey        = KEYS[1]
//                local hashKey        = KEYS[2]
//                local timeKey        = KEYS[3]
//                local uuId           = ARGV[1]
//                local bidAmount      = tonumber(ARGV[2])
//                local bidEventJson   = ARGV[3]
//                local auctionEndTime = ARGV[4]
//                local ttl            = tonumber(ARGV[5])
//
//                local exists = redis.call('ZCARD', zsetKey)
//                if exists == 0 then
//                    redis.call('ZADD', zsetKey, bidAmount, uuId)
//                    redis.call('HSET', hashKey, uuId, bidEventJson)
//                    redis.call('SET',  timeKey, auctionEndTime, 'EX', ttl)
//                    redis.call('EXPIRE', zsetKey, ttl)
//                    redis.call('EXPIRE', hashKey, ttl)
//                    return 1
//                else
//                    return 0
//                end
//                """;
//
//        Long result = bidStringRedisTemplate.execute(
//                new DefaultRedisScript<>(luaScript, Long.class),
//                List.of(bidZSetKey, bidHashKey, auctionTimeKey),
//                uuid,
//                String.valueOf(START_PRICE),
//                bidEventJson,
//                String.valueOf(auctionEndTimeMillis),
//                String.valueOf(ttlSeconds)
//        );
//
//        if (result == null || result != 1L) {
//            log.warn("[SEED] Redis 초기 세팅 실패 또는 이미 존재 result={}", result);
//        } else {
//            log.info("[SEED] Redis ZSET/Hash/auction_end_time 세팅 완료 productId={}, endTime={}",
//                    productId, auctionEndTime);
//        }
//
//        /* 7. DB Bid 초기 레코드 (베이스라인) */
//        Bid baseBid = new Bid();
//        baseBid.setUuid(uuid);
//        baseBid.setProduct(savedProduct);
//        baseBid.setUser(seller);
//        baseBid.setBidAmount(START_PRICE);
//        baseBid.setCreatedAt(now);
//        baseBid.setIsWinned(IsWinned.N);
//        bidRepository.save(baseBid);
//
//        log.info("[SEED] 완료  productId={}, auctionEndTime={}", productId, auctionEndTime);
//        log.info("[SEED] JMeter에서 사용할 productId = {}", productId);
//    }
//}
