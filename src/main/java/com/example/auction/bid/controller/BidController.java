package com.example.auction.bid.controller;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.dto.BidCreateDto;
import com.example.auction.bid.service.BidService;
import com.example.auction.common.dto.CommonResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bid")
@RequiredArgsConstructor
public class BidController {
    
    private final BidService bidService;
    @PostMapping("/")
    public ResponseEntity<?> bidProduct(@ModelAttribute BidCreateDto bidCreateDto) {
//        Bid bid =
                bidService.bidProduct(bidCreateDto);
        return ResponseEntity.ok(new CommonResDto(HttpStatus.OK, "상품 입찰 성공", null));
    }

}
