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
        name = "monthly_title_award",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_monthly_title_award_ym_type_rank",
                        columnNames = {"ym", "title_type", "rank"}
                )
        },
        indexes = {
                @Index(name = "idx_monthly_title_award_ym", columnList = "ym"),
                @Index(name = "idx_monthly_title_award_user", columnList = "user_id")
        }
)
public class MonthlyTitleAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7)
    private String ym;

    @Enumerated(EnumType.STRING)
    @Column(name = "title_type", nullable = false, length = 30)
    private SeasonTitleType titleType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 100)
    private String nicknameSnapshot;

    @Column(length = 500)
    private String profileImageUrlSnapshot;

    @Column(nullable = false)
    private Integer rank;
    private Long metricLong;
    private Double metricDouble;

    @Column(nullable = false)
    private Instant createdAt;
}
