package com.example.auction.bid.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.BidAllDto;
import com.example.auction.bid.dto.BidEvent;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BidService {

    private final RedissonClient bidRedissonClient;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> bidRedisTemplate;
    private final RedisTemplate<String, String> bidStringRedisTemplate;
    private final BidEventProducer bidEventProducer;
    private final ObjectMapper objectMapper;

    public BidService(
            @Qualifier("bidRedisson") RedissonClient bidRedissonClient,
            BidRepository bidRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            @Qualifier("bid") RedisTemplate<String, Object> bidRedisTemplate,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidStringRedisTemplate,
            BidEventProducer bidEventProducer,
            ObjectMapper objectMapper) {
        this.bidRedissonClient = bidRedissonClient;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.bidRedisTemplate = bidRedisTemplate;
        this.bidStringRedisTemplate = bidStringRedisTemplate;
        this.bidEventProducer = bidEventProducer;
        this.objectMapper = objectMapper;
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


//      redission을 활용한 락 비즈니스 로직
//        String lockKey = "lock_bid_" + bidCreateDto.getProductId();
//        RLock lock = bidRedissonClient.getLock(lockKey);
//        try {
//            lock.lock();
//            log.info("락 획득 성공 - Key: {}", lockKey);
//            Bid bid = saveBid(bidCreateDto, user, product);
//            return bid;
//        } finally {
//            if (lock.isHeldByCurrentThread()) {
//                lock.unlock();
//                log.info("락 해제 성공 - Key: {}", lockKey);
//            }
//        }
        return bidHotAuction(bidCreateDto, user, product);
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
        String bidZSetKey = "product_bid_zset_" + product.getProductId();
        String bidHashKey = "product_bid_hash_" + product.getProductId();

        BidEvent bidEvent = BidEvent.builder()
                .userId(user.getUserId())
                .productId(bidDto.getProductId())
                .bidAmount(bidDto.getBidAmount())
                .isWinned(bidDto.getIsWinned())
                .build();

        try {
            String bidEventJson = objectMapper.writeValueAsString(bidEvent);
            String uuid = UUID.randomUUID().toString();

            // Lua 스크립트 (최고가 비교 후 갱신)
            String luaScript = """
        local zsetKey = KEYS[1]
        local hashKey = KEYS[2]
        local uuId = ARGV[1]
        local bidAmount = tonumber(ARGV[2])
        local bidEventJson = ARGV[3]

        -- 현재 최고가 확인
        local currentMax = redis.call('ZREVRANGE', zsetKey, 0, 0, 'WITHSCORES')
        local currentScore = 0
        if #currentMax > 0 then
            currentScore = tonumber(currentMax[2])
        end

        -- 새로운 입찰가가 더 높아야 갱신
        if bidAmount > currentScore then
            redis.call('ZADD', zsetKey, bidAmount, uuId)
            redis.call('HSET', hashKey, uuId, bidEventJson)
            return 1
        else
            return 0
        end
        """;

            Long result = bidStringRedisTemplate.execute(
                    new DefaultRedisScript<>(luaScript, Long.class),
                    List.of(bidZSetKey, bidHashKey),
                    uuid,
                    String.valueOf(bidDto.getBidAmount()),
                    bidEventJson
            );

            if (result == null || result == 0) {
                throw new RuntimeException("현재 최고가보다 높은 금액만 입찰 가능합니다.");
            }

            log.info("Redis ZSET + Hash 입찰 성공 - ZSET Key: {}, Hash Key: {}, UUID: {}, 입찰가: {}",
                    bidZSetKey, bidHashKey, uuid, bidDto.getBidAmount());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("BidEvent JSON 변환 실패", e);
        }

        // 비동기 DB 저장 (정합성 보장용)
        bidEventProducer.publishBidEvent(bidDto, user);

        return bidDto.toBid(); // 임시 반환
    }


    // 콜드 경매
    @Transactional
    public Bid bidColdAuction(BidCreateDto bidDto, User user, Product product) {
        int retry = 3;
        while (retry-- > 0) {
            Product p = productRepository.findById(product.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product"));

            Bid recentBid = bidRepository.findTopByProduct_ProductIdOrderByCreatedAtDesc(product.getProductId())
                    .orElse(null);

            if (recentBid != null && bidDto.getBidAmount() <= recentBid.getBidAmount()) {
                throw new RuntimeException("현재가보다 높은 입찰가여야 합니다.");
            }

            try {
                Bid bid = bidDto.toBid();
                bid.setUser(user);
                bid.setProduct(product);
                bidRepository.save(bid);
                return bid;
            } catch (OptimisticLockException e) {
                // 재시도
            }
        }
        throw new RuntimeException("입찰 처리 중 오류 발생, 다시 시도해주세요.");
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