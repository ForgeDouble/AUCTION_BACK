package com.example.auction.bid.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.*;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
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

import java.time.LocalDateTime;
import java.util.*;
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
    private final BidWebsocketService bidWebsocketService;

    public BidService(
            @Qualifier("bidRedisson") RedissonClient bidRedissonClient,
            BidRepository bidRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            @Qualifier("bid") RedisTemplate<String, Object> bidRedisTemplate,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidStringRedisTemplate,
            BidEventProducer bidEventProducer,
            ObjectMapper objectMapper,
            BidWebsocketService bidWebsocketService) {
        this.bidRedissonClient = bidRedissonClient;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.bidRedisTemplate = bidRedisTemplate;
        this.bidStringRedisTemplate = bidStringRedisTemplate;
        this.bidEventProducer = bidEventProducer;
        this.objectMapper = objectMapper;
        this.bidWebsocketService = bidWebsocketService;
    }

    // 입찰 서비스
//    로직 보완 필요
    public BidEvent bidProduct(BidCreateDto bidCreateDto) {
//        String email = SecurityContextHolder.getContext().getAuthentication().getName();
//        User user = userRepository.findByEmailAndDelYn(email, DelYN.N)
//                .orElseThrow(() -> new ResourceNotFoundException("로그인 중인 User"));
        
//        websocket test용 코드
        User user = userRepository.findById(1L)
                .orElseThrow(() -> new ResourceNotFoundException("로그인 중인 User"));

        Product product = productRepository.findByProductIdAndDelYn(bidCreateDto.getProductId(), DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("Product"));

        if (product.getStatus() != Status.PROCESSING) {
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
        BidEvent bidEvent = bidHotAuction(bidCreateDto, user);

        bidWebsocketService.broadcastBidEvent(bidEvent);

        return bidEvent;
    }

    // bid Insert
//    @Transactional
//    protected Bid saveBid(BidCreateDto bidCreateDto, User user, Product product) {
//
//        Bid recentBid = bidRepository.findTopByProduct_ProductIdOrderByCreatedAtDesc(product.getProductId())
//                .orElseThrow(() -> new ResourceNotFoundException("최신 Bid"));
//
//        if (bidCreateDto.getBidAmount() < recentBid.getBidAmount()) {
//            throw new RuntimeException("이전 입찰가 보다 높아야합니다.");
//        } else if (bidCreateDto.getBidAmount() % 100 != 0) {
//            throw  new RuntimeException("입찰가는 100원 단위여야 합니다.");
//        }
//
//        Bid bid = bidCreateDto.toBid();
//        bid.setProduct(product);
//        bid.setUser(user);
//        bidRepository.save(bid);
//        log.info("입찰 완료 - ProductId: {}, UserId: {}", bidCreateDto.getProductId(), user.getUserId());
//
//        return bid;
//    }

    // 핫 경매
    private BidEvent bidHotAuction(BidCreateDto bidDto, User user) {
        String bidZSetKey = "product_bid_zset_" + bidDto.getProductId();
        String bidHashKey = "product_bid_hash_" + bidDto.getProductId();
        String auctionTimeKey = "auction_end_time_" + bidDto.getProductId();

        BidEvent bidEvent = BidEvent.builder()
                .userId(user.getUserId())
                .userName(user.getName())
                .productId(bidDto.getProductId())
                .bidAmount(bidDto.getBidAmount())
                .createdAt(LocalDateTime.now())
                .isWinned(bidDto.getIsWinned())
                .build();

        try {
            String bidEventJson = objectMapper.writeValueAsString(bidEvent);
            String uuid = UUID.randomUUID().toString();
            long currentTimeMillis = System.currentTimeMillis();
            // Lua 스크립트 (최고가 비교 후 갱신)
            String luaScript = """
        local zsetKey = KEYS[1]
        local hashKey = KEYS[2]
        local timeKey = KEYS[3]
        local uuId = ARGV[1]
        local bidAmount = tonumber(ARGV[2])
        local bidEventJson = ARGV[3]
        local currentTime = tonumber(ARGV[4])
        
        -- Redis에서 경매 종료 시간 조회
        local auctionEndTime = redis.call('GET', timeKey)
        if not auctionEndTime then
            return -2  -- 경매 정보 없음 (만료된 경매)
        end
        
        -- 경매 시간 검증
        if currentTime > tonumber(auctionEndTime) then
            return -1  -- 경매 시간 만료
        end
        
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
            return 1  -- 입찰 성공
        else
            return 0  -- 입찰가 부족
        end
        """;

            Long result = bidStringRedisTemplate.execute(
                    new DefaultRedisScript<>(luaScript, Long.class),
                    List.of(bidZSetKey, bidHashKey, auctionTimeKey),
                    uuid,
                    String.valueOf(bidDto.getBidAmount()),
                    bidEventJson,
                    String.valueOf(currentTimeMillis)
            );

            if (result == null) {
                throw new RuntimeException("입찰 처리 중 오류가 발생했습니다.");
            } else if (result == -2) {
                throw new RuntimeException("존재하지 않거나 만료된 경매입니다.");
            } else if (result == -1) {
                throw new RuntimeException("경매 시간이 종료되었습니다.");
            } else if (result == 0) {
                throw new RuntimeException("현재 최고가보다 높은 금액만 입찰 가능합니다.");
            }

            log.info("Redis ZSET + Hash 입찰 성공 - ProductId: {}, 입찰가: {}, 현재시간: {}",
                    bidDto.getProductId(), bidDto.getBidAmount(), currentTimeMillis);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("BidEvent JSON 변환 실패", e);
        }

        // 비동기 DB 저장 (정합성 보장용)
        bidEventProducer.publishBidEvent(bidEvent);

        return bidEvent; // 임시 반환
    }


    // 콜드 경매
//    @Transactional
//    public Bid bidColdAuction(BidCreateDto bidDto, User user, Product product) {
//        int retry = 3;
//        while (retry-- > 0) {
//            Product p = productRepository.findById(product.getProductId())
//                    .orElseThrow(() -> new ResourceNotFoundException("Product"));
//
//            Bid recentBid = bidRepository.findTopByProduct_ProductIdOrderByCreatedAtDesc(product.getProductId())
//                    .orElse(null);
//
//            if (recentBid != null && bidDto.getBidAmount() <= recentBid.getBidAmount()) {
//                throw new RuntimeException("현재가보다 높은 입찰가여야 합니다.");
//            }
//
//            try {
//                Bid bid = bidDto.toBid();
//                bid.setUser(user);
//                bid.setProduct(product);
//                bidRepository.save(bid);
//                return bid;
//            } catch (OptimisticLockException e) {
//                // 재시도
//            }
//        }
//        throw new RuntimeException("입찰 처리 중 오류 발생, 다시 시도해주세요.");
//    }

    // 특정 입찰 목록 조회 From Redis
    public List<BidEvent> getAllBidHistory(Long productId, boolean desc) {
        String zsetKey = "product_bid_zset_" + productId;
        String hashKey = "product_bid_hash_" + productId;

        // ZSET 전체 가져오기
        Set<String> uuids;
        if (desc) {
            uuids = bidStringRedisTemplate.opsForZSet().reverseRange(zsetKey, 0, -1); // 최신순
        } else {
            uuids = bidStringRedisTemplate.opsForZSet().range(zsetKey, 0, -1); // 오래된 순
        }

        log.info("입찰 데이터 uuid 조회 : {}" , uuids.toString());

        if (uuids == null || uuids.isEmpty()) {
            return Collections.emptyList();
        }

        // HASH에서 상세 데이터 조회
        List<Object> jsonList = bidStringRedisTemplate.opsForHash()
                .multiGet(hashKey, new ArrayList<>(uuids));
        log.info("입찰 데이터 json list 조회 : {}" , jsonList.toString());

        List<BidEvent> bidEvents = new ArrayList<>();
        for (Object obj : jsonList) {
            if (obj == null) continue;
            try {
                bidEvents.add(objectMapper.readValue(obj.toString(), BidEvent.class));
            } catch (JsonProcessingException e) {
                e.printStackTrace(); // 파싱 실패한 건 무시
            }
        }
        return bidEvents;
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

    /* 마이페이지 user 입찰 내역 조회 */
    @Transactional(readOnly = true)
    public List<BidAllByUserDto> readBidAllByUser() {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        List<BidAllByUserDto> bidListDto = bidRepository.findBidAllByUser(email);
//                .stream()
//                .collect(Collectors.toList());

        return bidListDto;
    }


}