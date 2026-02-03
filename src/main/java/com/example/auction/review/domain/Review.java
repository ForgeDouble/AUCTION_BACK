package com.example.auction.review.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import com.example.auction.product.domain.Product;
import com.example.auction.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "review", uniqueConstraints = {
            @UniqueConstraint(name = "uk_review_product_reviewer", columnNames = {"product_id", "reviewer_id"})
        })
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 리뷰 대상(판매자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    // 리뷰 작성자(낙찰자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    // 정책 : 내용 생략가능(선택)
    @Column(length = 2000)
    private String content;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_tag",joinColumns = @JoinColumn(name = "review_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 50)
    @Builder.Default
    private Set<ReviewTag> tags = new LinkedHashSet<>();


}
