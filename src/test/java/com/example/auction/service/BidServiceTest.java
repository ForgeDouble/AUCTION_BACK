package com.example.auction.service;

import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidCreateDto;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.bid.service.BidEventProducer;
import com.example.auction.bid.service.BidService;
import com.example.auction.bid.service.BidWebsocketService;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.push.service.PushService;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    RedissonClient bidRedissonClient;
    @Mock
    BidRepository bidRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    RedisTemplate<String, Object> bidRedisTemplate;
    @Mock
    RedisTemplate<String, String> bidStringRedisTemplate;
    @Mock
    BidEventProducer bidEventProducer;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    BidWebsocketService bidWebsocketService;
    @Mock
    PushService pushService;

    BidService bidService;  // @InjectMocks 제거

    @BeforeEach
    void setUp() {
        // 생성자로 직접 주입 (Qualifier 문제 해결)
        bidService = new BidService(
                bidRedissonClient,
                bidRepository,
                productRepository,
                userRepository,
                bidRedisTemplate,        // @Qualifier("bid")
                bidStringRedisTemplate,  // @Qualifier("bidPrice")
                bidEventProducer,
                objectMapper,
                bidWebsocketService,
                pushService
        );
    }

    @Test
    void 정상_입찰_성공() throws Exception {
        // Arrange - 유저 세팅
        User seller = mock(User.class);
        given(seller.getUserId()).willReturn(1L);

        User buyer = mock(User.class);
        given(buyer.getUserId()).willReturn(2L);
        given(buyer.getNickname()).willReturn("구매자");
        given(buyer.getProfileImageUrl()).willReturn("http://img.url");

        // Arrange - 상품 세팅
        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.PROCESSING);
        given(product.getUser()).willReturn(seller);

        // Arrange - Repository Mock
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        // Arrange - Redis Mock
        given(objectMapper.writeValueAsString(any()))
                .willReturn("{\"uuid\":\"test\"}");
        doReturn(1L)
                .when(bidStringRedisTemplate)
                .execute(
                        (RedisScript<String>) any(),
                        (List<String>) any(),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class)
                );

        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        given(bidStringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRange(any(), anyLong(), anyLong()))
                .willReturn(Collections.emptySet());

        // Arrange - DTO
        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);


        // Act
        BidEvent result = bidService.bidProduct(dto, "buyer@email.com");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(dto.getProductId());
        assertThat(result.getBidAmount()).isEqualTo(5000L);
        verify(bidWebsocketService, times(1)).broadcastBidEvent(any());
    }

    @Test
    void 입찰_실패_존재하지않는_유저() {
        // Arrange
        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);

        given(userRepository.findByEmailAndDelYn("notexist@email.com", DelYN.N))
                .willReturn(Optional.empty());  // 유저 없음

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "notexist@email.com"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("유효하지 않은 유저입니다.");
    }

    @Test
    void 입찰_실패_존재하지않는_상품() {
        // Arrange
        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);

        User buyer = mock(User.class);
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));  // 유저는 존재

        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.empty());  // 상품 없음

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "buyer@email.com"))
                .isInstanceOf(ResourceNotFoundException.class);

    }

    @Test
    void 입찰_실패_경매상태가_PROCESSING_아님() {
        // Arrange
        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);

        User buyer = mock(User.class);
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));

        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.READY);  // PROCESSING 아님
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "buyer@email.com"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("경매중인 상품이 아닙니다.");
    }

    @Test
    void 입찰_실패_판매자가_입찰시도() {
        // Arrange
        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);

        User seller = mock(User.class);
        given(seller.getUserId()).willReturn(1L);

        // 판매자와 입찰자가 동일한 유저
        given(userRepository.findByEmailAndDelYn("seller@email.com", DelYN.N))
                .willReturn(Optional.of(seller));

        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.PROCESSING);
        given(product.getUser()).willReturn(seller);  // 상품 판매자 = 입찰자
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "seller@email.com"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("판매자는 입찰 할 수 없습니다.");
    }

    @Test
    void 입찰_실패_음수_입찰가() {
        // Arrange
        BidCreateDto dto = new BidCreateDto(100L, -1000L, IsWinned.N);  // 음수

        User seller = mock(User.class);
        given(seller.getUserId()).willReturn(1L);

        User buyer = mock(User.class);
        given(buyer.getUserId()).willReturn(2L);
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));

        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.PROCESSING);
        given(product.getUser()).willReturn(seller);
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "buyer@email.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("입찰가는 1000원 단위로 입력해주세요.");
    }

    @Test
    void 입찰_실패_1000원_단위_아닌_입찰가() {
        // Arrange
        BidCreateDto dto = new BidCreateDto(100L, 1500L, IsWinned.N);  // 1000원 단위 아님

        User seller = mock(User.class);
        given(seller.getUserId()).willReturn(1L);

        User buyer = mock(User.class);
        given(buyer.getUserId()).willReturn(2L);
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));

        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.PROCESSING);
        given(product.getUser()).willReturn(seller);
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "buyer@email.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("입찰가는 1000원 단위로 입력해주세요.");
    }

    @Test
    void 입찰_실패_Redis_내부오류() throws Exception {
        // Arrange
        User seller = mock(User.class);
        given(seller.getUserId()).willReturn(1L);

        User buyer = mock(User.class);
        given(buyer.getUserId()).willReturn(2L);
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));

        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.PROCESSING);
        given(product.getUser()).willReturn(seller);
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        given(objectMapper.writeValueAsString(any()))
                .willReturn("{\"uuid\":\"test\"}");
        doReturn(null)  // result == null
                .when(bidStringRedisTemplate)
                .execute(
                        (RedisScript<String>) any(),
                        (List<String>) any(),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class)
                );

        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "buyer@email.com"))
                .isInstanceOf(InternalErrorException.class)
                .hasMessageContaining("입찰 처리중 내부 오류가 발생했습니다.");
    }

    @Test
    void 입찰_실패_만료된_경매() throws Exception {
        // Arrange
        User seller = mock(User.class);
        given(seller.getUserId()).willReturn(1L);

        User buyer = mock(User.class);
        given(buyer.getUserId()).willReturn(2L);
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));

        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.PROCESSING);
        given(product.getUser()).willReturn(seller);
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        given(objectMapper.writeValueAsString(any()))
                .willReturn("{\"uuid\":\"test\"}");
        doReturn(-2L)  // result == -2
                .when(bidStringRedisTemplate)
                .execute(
                        (RedisScript<String>) any(),
                        (List<String>) any(),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class)
                );

        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "buyer@email.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("존재하지 않거나 만료된 경매입니다.");
    }

    @Test
    void 입찰_실패_경매시간_초과() throws Exception {
        // Arrange
        User seller = mock(User.class);
        given(seller.getUserId()).willReturn(1L);

        User buyer = mock(User.class);
        given(buyer.getUserId()).willReturn(2L);
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));

        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.PROCESSING);
        given(product.getUser()).willReturn(seller);
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        given(objectMapper.writeValueAsString(any()))
                .willReturn("{\"uuid\":\"test\"}");
        doReturn(-1L)  // result == -1
                .when(bidStringRedisTemplate)
                .execute(
                        (RedisScript<String>) any(),
                        (List<String>) any(),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class)
                );

        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "buyer@email.com"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("접근할 권한이 없습니다.");
    }

    @Test
    void 입찰_실패_최고가보다_낮은_금액() throws Exception {
        // Arrange
        User seller = mock(User.class);
        given(seller.getUserId()).willReturn(1L);

        User buyer = mock(User.class);
        given(buyer.getUserId()).willReturn(2L);
        given(userRepository.findByEmailAndDelYn("buyer@email.com", DelYN.N))
                .willReturn(Optional.of(buyer));

        Product product = mock(Product.class);
        given(product.getStatus()).willReturn(Status.PROCESSING);
        given(product.getUser()).willReturn(seller);
        given(productRepository.findByProductIdAndDelYn(100L, DelYN.N))
                .willReturn(Optional.of(product));

        given(objectMapper.writeValueAsString(any()))
                .willReturn("{\"uuid\":\"test\"}");
        doReturn(0L)  // result == 0
                .when(bidStringRedisTemplate)
                .execute(
                        (RedisScript<String>) any(),
                        (List<String>) any(),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class)
                );

        BidCreateDto dto = new BidCreateDto(100L, 5000L, IsWinned.N);

        // Act & Assert
        assertThatThrownBy(() -> bidService.bidProduct(dto, "buyer@email.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("현재 최고가보다 높은 금액만 입찰 가능합니다.");
    }
}
