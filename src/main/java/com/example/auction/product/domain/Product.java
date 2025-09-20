package com.example.auction.product.domain;

import com.example.auction.category.domain.Category;
import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.product.dto.ProductUpdateDto;

import com.example.auction.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
public class Product extends BaseTimeEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;
    
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String productContent;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellYN sellYN;

    // 신고 관련 컬럼
    @Column(nullable = false)
    @Builder.Default
    private Boolean blocked = false; // 차단 여부
    private LocalDateTime blockedAt; // 차단 일 시
    @Column(length = 300)
    private String blockedReason; // 차단 사유
    
    public void update(ProductUpdateDto dto, Category category) {
    	this.category = category;
    	this.productName = dto.getProductName();
    	this.productContent = dto.getProductContent();
    	this.price = dto.getPrice();
    }

    public void block(String reason) {
        this.blocked = true;
        this.blockedAt = LocalDateTime.now();
        this.blockedReason = reason;
    }
    public void unblock() {
        this.blocked = false;
        this.blockedAt = null;
        this.blockedReason = null;
    }

    // 경매 종료 시간 계산 (24시간 고정)
    public LocalDateTime getAuctionEndTime() {
        return this.getCreatedAt().plusHours(24);
    }

    // 경매 상태 체크
    public boolean isAuctionActive() {
        LocalDateTime now = LocalDateTime.now();
        return sellYN == SellYN.N &&
                now.isBefore(getAuctionEndTime());
    }

    public boolean isAuctionEnded() {
        return sellYN == SellYN.Y ||
                LocalDateTime.now().isAfter(getAuctionEndTime());
    }

    // 남은 시간 계산
    public Duration getTimeRemaining() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = getAuctionEndTime();
        if (now.isAfter(endTime)) {
            return Duration.ZERO;
        }
        return Duration.between(now, endTime);
    }
}
