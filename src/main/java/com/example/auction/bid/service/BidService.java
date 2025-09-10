package com.example.auction.bid.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidAllDto;
import com.example.auction.bid.dto.BidWinnerDto;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.bid.dto.BidCreateDto;
import com.example.auction.product.domain.SellYN;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
//    로직 보완 필요
    public Bid bidProduct(BidCreateDto bidCreateDto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("로그인 중인 User"));

        Product product = productRepository.findByProductIdAndDelYn(bidCreateDto.getProductId(), DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));

        if (product.getSellYN() != SellYN.N) {
            throw new RuntimeException("경매중인 상품이 아닙니다.");
        } 
//        접속중인 유저가 판매자일 경우 입찰 불가능
//        개발중에는 주석 처리
//        else if (product.getUser().getUserId() == user.getUserId()) {
//            throw  new UnauthorizedAccessException("판매자는 입찰 할 수 없습니다.");
//        } 

        String lockKey = "lock_bid_" + bidCreateDto.getProductId();
        RLock lock = bidRedissonClient.getLock(lockKey);
        try {
            lock.lock();
            log.info("락 획득 성공 - Key: {}", lockKey);
            Bid bid = saveBid(bidCreateDto, user, product);
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
    protected Bid saveBid(BidCreateDto bidCreateDto, User user, Product product) {

        Bid recentBid = bidRepository.findTopByProduct_ProductIdOrderByCreatedAtDesc(product.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("최신 Bid"));

        if (bidCreateDto.getBidAmount() < recentBid.getBidAmount()) {
            throw new RuntimeException("이전 입찰가 보다 높아야합니다.");
        } else if (bidCreateDto.getBidAmount() % 100 != 0) {
            throw  new RuntimeException("입찰가는 100원 단위여야 합니다.");
        }

        Bid bid = bidCreateDto.toBid();
        bid.setProduct(product);
        bid.setUser(user);
        bidRepository.save(bid);
        log.info("입찰 완료 - ProductId: {}, UserId: {}", bidCreateDto.getProductId(), user.getUserId());

        return bid;
    }

    // 핫 경매
    private Bid bidHotAuction(BidCreateDto bidDto, User user, Product product) {
        String redisKey = "product_bid_" + product.getProductId();

        String luaScript = """
        local current = tonumber(redis.call('GET', KEYS[1]) or '0')
        local bid = tonumber(ARGV[1])
        if bid > current then
            redis.call('SET', KEYS[1], bid)
            return 1
        else
            return 0
        end
    """;

//        Long result = redisTemplate.execute(
//                new DefaultRedisScript<>(luaScript, Long.class),
//                List.of(redisKey),
//                bidDto.getBidAmount()
//        );

//        if (result == 0) {
//            throw new RuntimeException("현재가보다 높은 입찰가여야 합니다.");
//        }

        // 선택: 비동기 DB 저장 (정합성 보장용)
//        saveBidAsync(bidDto, user, product);

        return bidDto.toBid(); // 임시 반환
    }



    //   특정 입찰 목록 조회
    @Transactional(readOnly = true)
    public List<BidAllDto> readAllBidsByProductId(Long productId) {
        List<BidAllDto> bidAllDtos = bidRepository.findAllByProduct_ProductId(productId).stream()
                .map(BidAllDto::fromEntity)
                .collect(Collectors.toList());
        return bidAllDtos;
    }

//   최종 입찰자 조회
//   예외 처리 보완 필요
    @Transactional(readOnly = true)
    public BidWinnerDto readWinner(Long productId) {
        Bid bid = bidRepository.findByProduct_ProductIdAndIsWinned(productId, IsWinned.Y)
                .orElseThrow(() -> new ResourceNotFoundException("Bid"));

        BidWinnerDto bidWinnerDto = BidWinnerDto.fromEntity(bid);
        return bidWinnerDto;
    }

}