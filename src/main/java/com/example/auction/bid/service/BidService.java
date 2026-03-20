package com.example.auction.bid.service;

import com.example.auction.bid.domain.Bid;
import com.example.auction.bid.domain.IsWinned;
import com.example.auction.bid.dto.*;
import com.example.auction.bid.repository.BidRepository;
import com.example.auction.common.domain.DelYN;
import com.example.auction.common.exception.BadRequestException;
import com.example.auction.common.exception.InternalErrorException;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.common.exception.UnauthorizedAccessException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.example.auction.push.service.PushService;
import com.example.auction.user.domain.User;
import com.example.auction.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
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
    private final PushService pushService;

    // ── Micrometer 메트릭 ──────────────────────────────────────────
    // 입찰 성공 카운터
    private final Counter bidSuccessCounter;
    // 입찰 실패 카운터 (사유별 tag)
    private final MeterRegistry meterRegistry;
    // 입찰 처리 시간 (Lua 스크립트 포함)
    private final Timer bidProcessTimer;
    // ───────────────────────────────────────────────────────────────

    public BidService(
            @Qualifier("bidRedisson") RedissonClient bidRedissonClient,
            BidRepository bidRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            @Qualifier("bid") RedisTemplate<String, Object> bidRedisTemplate,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidStringRedisTemplate,
            BidEventProducer bidEventProducer,
            ObjectMapper objectMapper,
            BidWebsocketService bidWebsocketService,
            PushService pushService,
            MeterRegistry meterRegistry) {
        this.bidRedissonClient = bidRedissonClient;
        this.bidRepository = bidRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.bidRedisTemplate = bidRedisTemplate;
        this.bidStringRedisTemplate = bidStringRedisTemplate;
        this.bidEventProducer = bidEventProducer;
        this.objectMapper = objectMapper;
        this.bidWebsocketService = bidWebsocketService;
        this.pushService = pushService;
        this.meterRegistry = meterRegistry;

        // 메트릭 초기화
        this.bidSuccessCounter = Counter.builder("bid.success")
                .description("입찰 성공 횟수")
                .tag("application", "auction-back")
                .register(meterRegistry);

        this.bidProcessTimer = Timer.builder("bid.process.time")
                .description("입찰 처리 시간 (Redis Lua 스크립트 포함)")
                .tag("application", "auction-back")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // 입찰 실패 카운터 (사유별)
    private void incrementFailCounter(String reason) {
        Counter.builder("bid.fail")
                .description("입찰 실패 횟수")
                .tag("application", "auction-back")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    // 입찰 서비스
    public BidEvent bidProduct(BidCreateDto bidCreateDto, String userEmail) {
        User user = userRepository.findByEmailAndDelYn(userEmail, DelYN.N)
                .orElseThrow(() ->
                {
                    log.warn("[INVALID_USER] 존재하지 않거나 유효하지 않은 유저 email={}", userEmail);
                    throw new UnauthorizedAccessException("INVALID_USER", "유효하지 않은 유저입니다.");
                });

        Product product = productRepository.findByProductIdAndDelYn(bidCreateDto.getProductId(), DelYN.N)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다 productId: " + bidCreateDto.getProductId()));

        if (product.getStatus() != Status.PROCESSING) {
            log.warn("[PASSED_AUCTION] 경매의 진행상태가 PROCESSING 이 아님 productId={}, productStatus={}",
                    product.getProductId(), product.getStatus());
            incrementFailCounter("NOT_PROCESSING");
            throw new UnauthorizedAccessException("NOT_PROCESSING", "경매중인 상품이 아닙니다.");
        } else if (product.getUser().getUserId().equals(user.getUserId())) {
            incrementFailCounter("SELLER_NOT_ALLOWED");
            throw new UnauthorizedAccessException("SELLER_NOT_ALLOWED", "판매자는 입찰 할 수 없습니다.");
        } else if (bidCreateDto.getBidAmount() < 0) {
            log.warn("[INVALID_AMOUNT] 음수 입찰가 bidAmount={}", bidCreateDto.getBidAmount());
            incrementFailCounter("QUANTITY_ERROR");
            throw new BadRequestException("QUANTITY_ERROR", "입찰가는 1000원 단위로 입력해주세요.");
        } else if (bidCreateDto.getBidAmount() % 1000 != 0) {
            log.warn("[QUANTITY_ERROR] 1000원 단위가 아니거나 올바르지 않은 입찰가 bidAmount={}", bidCreateDto.getBidAmount());
            incrementFailCounter("QUANTITY_ERROR");
            throw new BadRequestException("QUANTITY_ERROR", "입찰가는 1000원 단위로 입력해주세요.");
        }

        // 입찰 처리 시간 측정 시작
        BidEvent bidEvent = bidProcessTimer.record(() -> bidHotAuction(bidCreateDto, user, product));

        try {
            bidWebsocketService.broadcastBidEvent(bidEvent);
        } catch (Exception e) {
            log.warn("[WEBSOCKET_ERROR] broadCast중 오류 발생 bidEvent={}", bidEvent.toString());
            throw new InternalErrorException("INTERNAL_SERVER_ERROR", "입찰 처리중 내부 오류가 발생했습니다.");
        }

        // 입찰 성공 카운터 증가
        bidSuccessCounter.increment();

        return bidEvent;
    }

    // 핫 경매
    private BidEvent bidHotAuction(BidCreateDto bidDto, User user, Product product) {
        String bidZSetKey = "product_bid_zset_" + bidDto.getProductId();
        String bidHashKey = "product_bid_hash_" + bidDto.getProductId();
        String auctionTimeKey = "auction_end_time_" + bidDto.getProductId();

        String uuid = UUID.randomUUID().toString();
        long currentTimeMillis = System.currentTimeMillis();
        BidEvent bidEvent = BidEvent.builder()
                .uuid(uuid)
                .userId(user.getUserId())
                .userNickName(user.getNickname())
                .productId(bidDto.getProductId())
                .bidAmount(bidDto.getBidAmount())
                .createdAt(LocalDateTime.now())
                .isWinned(bidDto.getIsWinned())
                .profileImageUrl(user.getProfileImageUrl())
                .build();

        try {
            String bidEventJson = objectMapper.writeValueAsString(bidEvent);
            String luaScript = """
        local zsetKey = KEYS[1]
        local hashKey = KEYS[2]
        local timeKey = KEYS[3]
        local uuId = ARGV[1]
        local bidAmount = tonumber(ARGV[2])
        local bidEventJson = ARGV[3]
        local currentTime = tonumber(ARGV[4])
        
        local auctionEndTime = redis.call('GET', timeKey)
        if not auctionEndTime then
            return -2
        end
        
        if currentTime > tonumber(auctionEndTime) then
            return -1
        end
        
        local currentMax = redis.call('ZREVRANGE', zsetKey, 0, 0, 'WITHSCORES')
        local currentScore = 0
        if #currentMax > 0 then
            currentScore = tonumber(currentMax[2])
        end
        
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
                    List.of(bidZSetKey, bidHashKey, auctionTimeKey),
                    uuid,
                    String.valueOf(bidDto.getBidAmount()),
                    bidEventJson,
                    String.valueOf(currentTimeMillis)
            );

            if (result == null) {
                incrementFailCounter("REDIS_ERROR");
                throw new InternalErrorException("INTERNAL_SERVER_ERROR", "입찰 처리중 내부 오류가 발생했습니다.");
            } else if (result == -2) {
                incrementFailCounter("DATA_NOT_FOUND");
                throw new ResourceNotFoundException("DATA_NOT_FOUND", "존재하지 않거나 만료된 경매입니다.");
            } else if (result == -1) {
                incrementFailCounter("NOT_PROCESSING");
                throw new UnauthorizedAccessException("NOT_PROCESSING", "접근할 권한이 없습니다.");
            } else if (result == 0) {
                log.warn("[LOW_PRICE] 최고가보다 낮은 금액으로 입찰 bidAmount={}", bidDto.getBidAmount());
                incrementFailCounter("LOW_PRICE");
                throw new BadRequestException("LOW_PRICE", "현재 최고가보다 높은 금액만 입찰 가능합니다.");
            }

            log.info("Redis ZSET + Hash 입찰 성공 - ProductId: {}, 입찰가: {}, 현재시간: {}",
                    bidDto.getProductId(), bidDto.getBidAmount(), currentTimeMillis);

        } catch (JsonProcessingException e) {
            log.warn("[JSON_ERROR] BidEvent JSON 변환 실패 bidEvent={}", bidEvent.toString());
            incrementFailCounter("JSON_ERROR");
            throw new InternalErrorException("INTERNAL_SERVER_ERROR", "입찰 처리중 내부 오류가 발생했습니다.");
        }

        try {
            handleOutbid(bidEvent, product);
        } catch (Exception e) {
            log.warn("[BidOutbid] Outbid 처리 중 예외 productId={}", bidDto.getProductId(), e);
        }
        try {
            bidEventProducer.publishBidEvent(bidEvent);
        } catch (Exception e) {
            log.warn("[RABBITMQ_ERROR] RabbitMQ DB INSERT 처리 중 예외 productId={}", bidDto.getProductId(), e);
        }
        return bidEvent;
    }

    // 특정 입찰 목록 조회 From Redis
    public List<BidEvent> getAllBidHistory(Long productId, boolean desc) {
        String zsetKey = "product_bid_zset_" + productId;
        String hashKey = "product_bid_hash_" + productId;

        Set<String> uuids;
        if (desc) {
            uuids = bidStringRedisTemplate.opsForZSet().reverseRange(zsetKey, 0, -1);
        } else {
            uuids = bidStringRedisTemplate.opsForZSet().range(zsetKey, 0, -1);
        }

        log.info("입찰 데이터 uuid 조회 : {}", uuids.toString());

        if (uuids == null || uuids.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> jsonList = bidStringRedisTemplate.opsForHash()
                .multiGet(hashKey, new ArrayList<>(uuids));
        log.info("입찰 데이터 json list 조회 : {}", jsonList.toString());

        List<BidEvent> bidEvents = new ArrayList<>();
        for (Object obj : jsonList) {
            if (obj == null) continue;
            try {
                bidEvents.add(objectMapper.readValue(obj.toString(), BidEvent.class));
            } catch (JsonProcessingException e) {
                log.error("[JsonProcessingException] message={}, cause={}",
                        e.getMessage(),
                        e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none",
                        e);
            }
        }
        return bidEvents;
    }

    @Transactional(readOnly = true)
    public List<BidAllDto> readAllBidsByProductId(Long productId) {
        List<BidAllDto> bidAllDtos = bidRepository.findAllByProduct_ProductIdOrderByCreatedAtDesc(productId).stream()
                .map(BidAllDto::fromEntity)
                .collect(Collectors.toList());
        return bidAllDtos;
    }

    @Transactional(readOnly = true)
    public BidWinnerDto readWinner(Long productId) {
        Bid bid = bidRepository.findByProduct_ProductIdAndIsWinned(productId, IsWinned.Y)
                .orElseThrow(() -> new ResourceNotFoundException("Bid"));
        return BidWinnerDto.fromEntity(bid);
    }

    @Transactional(readOnly = true)
    public Page<BidAllByUserDto> readBidAllByUser(int page, int size, List<Status> statuses, IsWinned isWinned) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return bidRepository.findBidAllByUser(email, statuses, isWinned, pageable);
    }

    private void handleOutbid(BidEvent currentBid, Product product) {
        Long productId = currentBid.getProductId();
        List<BidEvent> history = getAllBidHistory(productId, true);
        if (history == null || history.size() < 2) return;

        BidEvent top = history.get(0);
        BidEvent previous = history.get(1);

        if (!Objects.equals(top.getUserId(), currentBid.getUserId())
                || !Objects.equals(top.getBidAmount(), currentBid.getBidAmount())) {
            return;
        }
        if (Objects.equals(previous.getUserId(), currentBid.getUserId())) return;

        sendOutbidNotification(previous, currentBid, product);
    }

    private void sendOutbidNotification(BidEvent previous, BidEvent current, Product product) {
        Long loserUserId = previous.getUserId();
        if (loserUserId == null) {
            log.warn("[BidOutbid] previous userId 가 null 입니다. productId={}", current.getProductId());
            return;
        }

        String productName = (product != null && product.getProductName() != null && !product.getProductName().isBlank())
                ? product.getProductName() : "경매 상품";

        String lastStr = NumberFormat.getInstance(Locale.KOREA).format(previous.getBidAmount());
        String newStr = NumberFormat.getInstance(Locale.KOREA).format(current.getBidAmount());

        String title = "입찰가가 추월되었습니다";
        String body = "[" + productName + "] 경매에서 " + lastStr + "원 입찰이 " + newStr + "원에 의해 추월되었습니다.";

        Map<String, String> data = Map.of(
                "type", "BID_OUTBID",
                "productId", String.valueOf(current.getProductId()),
                "previousAmount", String.valueOf(previous.getBidAmount()),
                "newAmount", String.valueOf(current.getBidAmount())
        );

        try {
            int success = pushService.sendToUser(loserUserId, title, body, data);
            log.info("[BidOutbid] outbid 푸시 전송 완료 productId={}, loserId={}, success={}",
                    current.getProductId(), loserUserId, success);
        } catch (Exception e) {
            log.warn("[BidOutbid] outbid 푸시 전송 실패 productId={}, loserId={}",
                    current.getProductId(), loserUserId, e);
        }
    }
}