package com.example.auction.bid.service;

import com.example.auction.bid.dto.BidCreateDto;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class BidEventProducer {

    private final BidRepository bidRepository;
    private final RabbitTemplate rabbitTemplate;

    public void publishBidEvent(BidEvent bidEvent) {
        rabbitTemplate.convertAndSend("bid-exchange", "bid-routing-key", bidEvent);
    }
}
