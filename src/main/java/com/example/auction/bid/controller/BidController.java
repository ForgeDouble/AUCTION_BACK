package com.example.auction.bid.controller;


import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.dto.*;

import com.example.auction.bid.service.BidService;
import com.example.auction.common.dto.CommonResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

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
    public void bidProductWeb(Principal principal, BidCreateDto bidCreateDto) {
        String userEmail = principal.getName();
        log.info("userEmail : {}", userEmail);
        BidEvent bidEvent = bidService.bidProduct(bidCreateDto, userEmail);
    }

    //    productId로 입찰 목록 조회
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
    public ResponseEntity<?> getBidAllByUser() {
        List<BidAllByUserDto> bidAllByUserDtos = bidService.readBidAllByUser();
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "입찰 목록 조회 성공", bidAllByUserDtos));
    }

}
