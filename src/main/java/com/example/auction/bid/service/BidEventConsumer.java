package com.example.auction.bid.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.product.domain.Product;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BidEventConsumer {
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @RabbitListener(queues = "bid-queue")
    public void consumeBidEvent(BidEvent event) {
        Product product = productRepository.findById(event.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Bid bid = new Bid();
        bid.setUuid(event.getUuid());
        bid.setProduct(product);
        bid.setUser(user);
        bid.setBidAmount(event.getBidAmount());
        bid.setCreatedAt(event.getCreatedAt());
        bid.setIsWinned(event.getIsWinned());
        bidRepository.save(bid);

        log.info("입찰 로그 DB 저장 완료 - ProductId: " + product.getProductId() + ", UserId: " + user.getUserId());
    }

}
