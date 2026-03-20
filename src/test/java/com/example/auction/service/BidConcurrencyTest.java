package com.example.auction.service;

import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidCreateDto;
import com.example.auction.bid.service.BidService;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/cleanup-data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class BidConcurrencyTest {

    @Autowired
    BidService bidService;

    @Autowired
    @Qualifier("bidPrice")
    RedisTemplate<String, String> bidStringRedisTemplate;

    @Autowired
    ProductRepository productRepository;

    private Long productId;

    private static final String ZSET_KEY_PREFIX = "product_bid_zset_";
    private static final String HASH_KEY_PREFIX = "product_bid_hash_";
    private static final String TIME_KEY_PREFIX = "auction_end_time_";

    @BeforeEach
    void setUp() {
        // 테스트용 상품 ID 조회 후 Redis 경매 시간 세팅
        Product product = productRepository.findAll().stream()
                .filter(p -> p.getProductName().equals("test-product"))
                .findFirst()
                .orElseThrow();

        productId = product.getProductId();
        long endTime = System.currentTimeMillis() + (60 * 60 * 1000);
        bidStringRedisTemplate.opsForValue()
                .set(TIME_KEY_PREFIX + productId, String.valueOf(endTime));
    }

    @Test
    void 동시에_500명이_입찰시_최고가_1개만_저장() throws InterruptedException {

        int threadCount = 500;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= threadCount; i++) {
            final long bidAmount = i * 1000L;
            final String email = "user" + i + "@test.com";

            executorService.submit(() -> {
                try {
                    BidCreateDto dto = new BidCreateDto(productId, bidAmount, IsWinned.N);
                    bidService.bidProduct(dto, email);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Redis에서 최고가 조회
        String topUuid = bidStringRedisTemplate.opsForZSet()
                .reverseRange(ZSET_KEY_PREFIX + productId, 0, 0)
                .iterator().next();
        String topJson = (String) bidStringRedisTemplate.opsForHash()
                .get(HASH_KEY_PREFIX + productId, topUuid);
        long totalCount = bidStringRedisTemplate.opsForZSet()
                .size(ZSET_KEY_PREFIX + productId);

        System.out.println("========== 동시성 테스트 결과 ==========");
        System.out.println("전체 스레드  : " + threadCount);
        System.out.println("성공 횟수    : " + successCount.get());
        System.out.println("실패 횟수    : " + failCount.get());
        System.out.println("ZSET 저장 수 : " + totalCount);
        System.out.println("최고 입찰가  : " + topJson);
        System.out.println("========================================");

        // 검증 - 최고가는 반드시 500000원
        assertThat(topJson).contains("500000");
        assertThat(successCount.get()).isGreaterThan(0);
    }
}
