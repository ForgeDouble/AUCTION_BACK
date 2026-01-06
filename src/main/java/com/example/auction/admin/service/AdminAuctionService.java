package com.example.auction.admin.service;

import com.example.auction.admin.dto.AdminAuctionActionReq;
import com.example.auction.admin.dto.AdminAuctionRowDto;
import com.example.auction.bid.dto.BidEvent;
import com.example.auction.common.exception.ResourceNotFoundException;
import com.example.auction.product.domain.Product;
import com.example.auction.product.domain.Status;
import com.example.auction.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class AdminAuctionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY_AUCTION_START = "auction:start:";

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    // DB3
    private final RedisTemplate<String, String> bidPriceRedis;
    private final RedisTemplate<String, Object> bidRedis;

    public AdminAuctionService(
            ProductRepository productRepository,
            ObjectMapper objectMapper,
            @Qualifier("bidPrice") RedisTemplate<String, String> bidPriceRedis,
            @Qualifier("bid") RedisTemplate<String, Object> bidRedis
    ) {
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
        this.bidPriceRedis = bidPriceRedis;
        this.bidRedis = bidRedis;
    }

    @Transactional(readOnly = true)
    public List<AdminAuctionRowDto> list(int size) {
        int limit = Math.max(1, Math.min(size, 300));
        List<Product> products = productRepository.findAdminMonitoring(PageRequest.of(0, limit));

        List<AdminAuctionRowDto> auctionRowDtos = new ArrayList<>(products.size());
        for (Product product : products) {
            Long pid = product.getProductId();

            String zKey = "product_bid_zset_" + pid;
            long bidCount = safeZCard(zKey);
            long currentBid = safeTopScore(zKey);

            if (currentBid <= 0) currentBid = (product.getPrice() == null ? 0L : product.getPrice());

            String sellerMasked = maskSeller(product);
            String category = (product.getCategory() == null) ? "미분류" : product.getCategory().getCategoryName();

            String endsAtIso = product.getAuctionEndTime()
                    .atZone(KST).toInstant().toString();

            String status = Boolean.TRUE.equals(product.getBlocked())
                    ? "BLOCKED"
                    : (product.getStatus() == null ? "UNKNOWN" : product.getStatus().name());

            auctionRowDtos.add(new AdminAuctionRowDto(
                    String.valueOf(pid),
                    product.getProductName(),
                    sellerMasked,
                    category,
                    currentBid,
                    bidCount,
                    endsAtIso,
                    status
            ));
        }
        return auctionRowDtos;
    }

    @Transactional
    public void suspend(long productId, AdminAuctionActionReq adminAuctionActionReq) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품이 없습니다."));

        String reason = (adminAuctionActionReq == null || adminAuctionActionReq.reason() == null) ? "" : adminAuctionActionReq.reason();
        product.block(reason);
        productRepository.save(product);
    }

    @Transactional
    public void forceEnd(long productId, AdminAuctionActionReq adminAuctionActionReq) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("상품이 없습니다."));

        // 이미 종료 상태면 그냥 반환
        if (product.getStatus() == Status.SELLED || product.getStatus() == Status.NOTSELLED) {
            cleanupRedisKeys(productId);
            return;
        }

        // Redis -> 최고 입찰/입찰수 -> 낙찰 여부 결정
        String bidZSetKey = "product_bid_zset_" + productId;
        String bidHashKey = "product_bid_hash_" + productId;

        Long zcountObj = bidPriceRedis.opsForZSet().size(bidZSetKey);
        long zcount = (zcountObj == null ? 0L : zcountObj);

        Set<String> winners = bidPriceRedis.opsForZSet().reverseRange(bidZSetKey, 0, 0);

        BidEvent winnerBid = null;
        if (winners != null && !winners.isEmpty()) {
            String winnerUuid = winners.iterator().next();
            Object raw = bidPriceRedis.opsForHash().get(bidHashKey, winnerUuid);
            if (raw != null) {
                try {
                    winnerBid = objectMapper.readValue(raw.toString(), BidEvent.class);
                } catch (Exception ignore) {}
            }
        }

        boolean hasRealWinner =
                (zcount >= 2) && (winnerBid != null) && (winnerBid.getUserId() != null);

        if (hasRealWinner) {
            product.updateStatus(Status.SELLED);
        } else {
            product.updateStatus(Status.NOTSELLED);
        }

        productRepository.save(product);
        cleanupRedisKeys(productId);

    }

    private long safeZCard(String zKey) {
        try {
            Long v = bidPriceRedis.opsForZSet().zCard(zKey);
            return v == null ? 0L : v;
        } catch (Exception e) {
            return 0L;
        }
    }

    private long safeTopScore(String zKey) {
        try {
            ZSetOperations<String, String> zSetOperations = bidPriceRedis.opsForZSet();
            Set<ZSetOperations.TypedTuple<String>> top = zSetOperations.reverseRangeWithScores(zKey, 0, 0);
            if (top == null || top.isEmpty()) return 0L;
            Double score = top.iterator().next().getScore();
            return score == null ? 0L : score.longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void cleanupRedisKeys(long productId) {
        try {
            bidPriceRedis.delete("product_bid_zset_" + productId);
            bidPriceRedis.delete("product_bid_hash_" + productId);
            bidPriceRedis.delete("auction_end_time_" + productId);
            bidRedis.delete(KEY_AUCTION_START + productId);
        } catch (Exception ignore) {}
    }

    private String maskSeller(Product p) {
        String nick = (p.getUser() != null && p.getUser().getNickname() != null)
                ? p.getUser().getNickname()
                : (p.getUser() != null ? p.getUser().getEmail() : "seller");

        if (nick == null || nick.isBlank()) return "seller";
        if (nick.length() <= 2) return nick.charAt(0) + "*";
        if (nick.length() == 3) return nick.charAt(0) + "*" + nick.charAt(2);
        return nick.substring(0, 2) + "***";
    }
}
