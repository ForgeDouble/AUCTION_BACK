package com.example.auction.bid.controller;

import com.example.auction.bid.dto.*;
import com.example.auction.bid.service.BidService;
import com.example.auction.common.dto.CommonResDto;
import com.example.auction.product.domain.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/bid")
@RequiredArgsConstructor
public class BidController {
    
    private final BidService bidService;
    private final SimpMessagingTemplate messagingTemplate;

//    입찰
//    @PostMapping("/bid")
//    public ResponseEntity<?> bidProduct(@ModelAttribute BidCreateDto bidCreateDto) {
//        BidEvent bidEvent = bidService.bidProduct(bidCreateDto);
//        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 입찰 성공", bidEvent));
//    }

    //    입찰 - websocket 버전
    @MessageMapping("/bid")
    @SendToUser("/queue/bid_response")
    public BidResponseDto bidProductWeb(@Header("simpSessionAttributes") Map<String, Object> attrs, BidCreateDto bidCreateDto) {
        try {
            String email = (String) attrs.get("email");
            log.info("User: " + email);
            BidEvent bidEvent = bidService.bidProduct(bidCreateDto, email);
            return BidResponseDto.success(bidEvent);
        } catch (IllegalArgumentException e) {
            // 비즈니스 로직 예외 (현재 최고가보다 낮음, 경매 종료 등)
            log.warn("[WebSocket] 입찰 실패 (검증 오류) - {}", e.getMessage());
            return BidResponseDto.error(e.getMessage(), "BID_VALIDATION_ERROR");
        } catch (Exception e) {
            log.warn("[WebSocket] 입찰 실패 (예외 처리) - {}", e.getMessage());
            return BidResponseDto.error(e.getMessage(), "INTERNAL_ERROR");
        }
    }

    //    redis로 입찰 목록 조회
    @GetMapping("/redis/{productId}")
    public ResponseEntity<?> getBidHistory(
            @PathVariable("productId") Long productId,
            @RequestParam(defaultValue = "true") boolean desc
    ) {
        List<BidEvent> bids = bidService.getAllBidHistory(productId, desc);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "입찰 목록 조회 성공", bids));
    }


//    productId로 입찰 목록 조회
    @GetMapping("/all/{productId}")
    public ResponseEntity<?> getAllBidsByproductId(@PathVariable("productId") Long productId) {
        List<BidAllDto> bidAllDtos = bidService.readAllBidsByProductId(productId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "입찰 목록 조회 성공", bidAllDtos));
    }

//   최종 입찰자 조회
    @GetMapping("/winner/{productId}")
    public ResponseEntity<?> getWinnerByProductId(@PathVariable("productId") Long productId) {
        BidWinnerDto bidWinnerDto = bidService.readWinner(productId);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "입찰자 조회 성공", bidWinnerDto));
    }

    /* 마이페이지 user 입찰 내역 조회 */
    @GetMapping("/allByUser")
    public ResponseEntity<?> getBidAllByUser(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status
    ) {
        Status statusEnum = status != null ? Status.valueOf(status.toUpperCase()) : null;
        Page<BidAllByUserDto> bidAllByUserDtos = bidService.readBidAllByUser(page, size, statusEnum);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "입찰 목록 조회 성공", bidAllByUserDtos));
    }

}
