package com.example.auction.controller;

import com.example.auction.bid.controller.BidController;
import com.example.auction.bid.dto.BidCreateDto;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.bid.dto.BidResponseDto;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.service.BidService;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BidControllerTest {

    @Mock
    BidService bidService;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    BidController bidController;

    Map<String, Object> attrs;
    BidCreateDto dto;

    @BeforeEach
    void setUp() {
        attrs = Map.of("email", "buyer@email.com");
        dto = new BidCreateDto(100L, 5000L, IsWinned.N);
    }

    @Test
    void 정상_입찰_성공() {
        // Arrange
        BidEvent bidEvent = BidEvent.builder()
                .productId(100L)
                .bidAmount(5000L)
                .build();
        given(bidService.bidProduct(any(), eq("buyer@email.com")))
                .willReturn(bidEvent);

        // Act
        BidResponseDto result = bidController.bidProductWeb(attrs, dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void 입찰_실패_권한없음() {
        // Arrange
        given(bidService.bidProduct(any(), eq("buyer@email.com")))
                .willThrow(new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다."));

        // Act
        BidResponseDto result = bidController.bidProductWeb(attrs, dto);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_USER");
    }

    @Test
    void 입찰_실패_잘못된요청() {
        // Arrange
        given(bidService.bidProduct(any(), eq("buyer@email.com")))
                .willThrow(new BadRequestException("LOW_PRICE", "현재 최고가보다 높은 금액만 입찰 가능합니다."));

        // Act
        BidResponseDto result = bidController.bidProductWeb(attrs, dto);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("LOW_PRICE");
    }

    @Test
    void 입찰_실패_서버내부오류() {
        // Arrange
        given(bidService.bidProduct(any(), eq("buyer@email.com")))
                .willThrow(new InternalErrorException("INTERNAL_SERVER_ERROR", "입찰 처리중 내부 오류가 발생했습니다."));

        // Act
        BidResponseDto result = bidController.bidProductWeb(attrs, dto);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void 입찰_실패_조회실패() {
        // Arrange
        given(bidService.bidProduct(any(), eq("buyer@email.com")))
                .willThrow(new ResourceNotFoundException("DATA_NOT_FOUND", "존재하지 않거나 만료된 경매입니다."));

        // Act
        BidResponseDto result = bidController.bidProductWeb(attrs, dto);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("DATA_NOT_FOUND");
    }

    @Test
    void 입찰_실패_알수없는예외() {
        // Arrange
        given(bidService.bidProduct(any(), eq("buyer@email.com")))
                .willThrow(new RuntimeException("알수없는 오류"));

        // Act
        BidResponseDto result = bidController.bidProductWeb(attrs, dto);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}