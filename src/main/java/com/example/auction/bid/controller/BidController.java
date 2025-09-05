package com.example.auction.bid.controller;


import com.example.auction.bid.dto.BidAllDto;
import com.example.auction.bid.dto.BidCreateDto;

import com.example.auction.bid.dto.BidWinnerDto;
import com.example.auction.bid.service.BidService;
import com.example.auction.common.dto.CommonResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bid")
@RequiredArgsConstructor
public class BidController {
    
    private final BidService bidService;

//    입찰
    @PostMapping("/bid")
    public ResponseEntity<?> bidProduct(@ModelAttribute BidCreateDto bidCreateDto) {
        bidService.bidProduct(bidCreateDto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 입찰 성공", null));
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

}
