package com.example.auction.bid.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import com.example.auction.bid.dto.BidCreateDto;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BidService {

    private final RedissonClient bidRedissonClient;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public BidService(
            @Qualifier("bidRedisson") RedissonClient bidRedissonClient,
            BidRepository bidRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.bidRedissonClient = bidRedissonClient;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // 입찰 서비스
    public Bid bidProduct(BidCreateDto bidCreateDto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 유저입니다."));

        String lockKey = "lock_bid_" + bidCreateDto.getProductId();
        RLock lock = bidRedissonClient.getLock(lockKey);
        try {
            lock.lock();
            log.info("락 획득 성공 - Key: {}", lockKey);
            Bid bid = saveBid(bidCreateDto, user);
            return bid;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("락 해제 성공 - Key: {}", lockKey);
            }
        }
    }

    // bid Insert
    @Transactional
    protected Bid saveBid(BidCreateDto bidCreateDto, User user) {
        Product product = productRepository.findByProductIdAndDelYn(bidCreateDto.getProductId(), DelYN.N)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 상품입니다."));
        Bid bid = bidCreateDto.toBid();
        bid.setProduct(product);
        bid.setUser(user);
        bidRepository.save(bid);
        log.info("입찰 완료 - ProductId: {}, UserId: {}", bidCreateDto.getProductId(), user.getUserId());

        return bid;
    }
}
