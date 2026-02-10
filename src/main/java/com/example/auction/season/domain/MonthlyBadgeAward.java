package com.example.auction.season.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "monthly_badge_award",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_monthly_badge_award_ym_type_rank",
                        columnNames = {"ym", "badge_type", "award_rank"}
                )
        },
        indexes = {
                @Index(name = "idx_monthly_badge_award_ym", columnList = "ym"),
                @Index(name = "idx_monthly_badge_award_user", columnList = "user_id")
        }
)
public class MonthlyBadgeAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7)
    private String ym;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false, length = 30)
    private SeasonBadgeType badgeType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 100)
    private String nicknameSnapshot;

    @Column(length = 500)
    private String profileImageUrlSnapshot;

    @Column(name = "award_rank", nullable = false)
    private Integer rank;

    @Column(nullable = false)
    private Long tagCount;

    @Column(nullable = false)
    private Long totalReviews;

    @Column(nullable = false)
    private Double ratio;

    @Column(nullable = false)
    private Instant createdAt;
}
