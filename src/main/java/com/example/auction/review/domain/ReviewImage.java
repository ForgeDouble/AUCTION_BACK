package com.example.auction.review.domain;

import com.example.auction.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "review_image")
public class ReviewImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false, length = 500)
    private String s3Key;

    @Column(nullable = false)
    private Integer position;
}
