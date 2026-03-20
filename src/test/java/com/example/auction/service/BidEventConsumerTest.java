package com.example.auction.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.bid.service.BidEventProducer;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Sql(
        scripts = "/test-data.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
@Sql(
        scripts = "/cleanup-data.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class BidEventConsumerTest {

    @Autowired
    BidEventProducer bidEventProducer;
    @Autowired BidRepository bidRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;

    private Long productId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Product product = productRepository.findAll().stream()
                .filter(p -> p.getProductName().equals("test-product"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("test-product not found in DB!"));
        productId = product.getProductId();

        System.out.println("===== productId: " + productId + " ====="); // 추가

        User user = userRepository.findByEmail("user1@test.com")
                .orElseThrow(() -> new RuntimeException("user1@test.com not found in DB!"));
        userId = user.getUserId();

        System.out.println("===== userId: " + userId + " ====="); // 추가
    }

    @Test
    void 메시지_발행시_DB에_저장되는지() {
        // given
        BidEvent event = BidEvent.builder()
                .uuid("test-uuid-1")
                .userId(userId)
                .userNickName("nick1")
                .productId(productId)
                .bidAmount(10000L)
                .createdAt(LocalDateTime.now())
                .isWinned(IsWinned.N)
                .build();

        // when
        bidEventProducer.publishBidEvent(event);

        // then - Consumer가 비동기로 처리하므로 최대 5초 대기
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            boolean exists = bidRepository.findAll().stream()
                    .anyMatch(b -> "test-uuid-1".equals(b.getUuid()));
            assertThat(exists).isTrue();
        });
    }

    @Test
    void 여러_메시지_순서대로_DB에_저장되는지() {
        int count = 10;

        for (int i = 1; i <= count; i++) {
            BidEvent event = BidEvent.builder()
                    .uuid("test-uuid-" + i)
                    .userId(userId)
                    .userNickName("nick" + i)
                    .productId(productId)
                    .bidAmount(i * 1000L)
                    .createdAt(LocalDateTime.now())
                    .isWinned(IsWinned.N)
                    .build();
            bidEventProducer.publishBidEvent(event);
        }

        List<String> expected = IntStream.rangeClosed(1, count)
                .mapToObj(i -> "test-uuid-" + i)
                .toList();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<String> uuids = bidRepository.findAllByOrderByBidIdAsc().stream()
                    .map(Bid::getUuid)
                    .filter(uuid -> uuid != null && uuid.startsWith("test-uuid-"))
                    .toList();
            assertThat(uuids).containsExactlyElementsOf(expected);
        });
    }

    @Test
    void 존재하지_않는_product이면_예외발생() {
        BidEvent event = BidEvent.builder()
                .uuid("test-uuid-fail")
                .userId(userId)
                .userNickName("nick1")
                .productId(999999L)
                .bidAmount(10000L)
                .createdAt(LocalDateTime.now())
                .isWinned(IsWinned.N)
                .build();

        bidEventProducer.publishBidEvent(event);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            boolean exists = bidRepository.findAll().stream()
                    .anyMatch(b -> "test-uuid-fail".equals(b.getUuid())); // ← null-safe로 수정
            assertThat(exists).isFalse();
        });
    }
}