package com.example.auction.bid.service;

import com.example.auction.bid.dto.BidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BidWebsocketService {

    private final SimpMessagingTemplate messagingTemplate;

    // 특정 경매 상품의 실시간 입찰 정보를 브로드캐스트
    public void broadcastBidEvent(BidEvent bidEvent) {
        messagingTemplate.convertAndSend("/topic/auction/" + bidEvent.getProductId(), bidEvent);
    }
}
