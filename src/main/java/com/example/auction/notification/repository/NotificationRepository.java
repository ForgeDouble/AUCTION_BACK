package com.example.auction.notification.repository;


import com.example.auction.common.domain.DelYN;
import com.example.auction.notification.domain.Notification;
import com.example.auction.notification.domain.NotificationCategory;
import com.example.auction.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 전체 탭: 유저별 + delYn 조건 + 최신순
    Page<Notification> findByUserAndDelYnOrderByCreatedAtDesc(
            User user,
            DelYN delYn,
            Pageable pageable
    );

    // 카테고리 탭: 유저별 + 카테고리 + delYn + 최신순
    Page<Notification> findByUserAndCategoryAndDelYnOrderByCreatedAtDesc(
            User user,
            NotificationCategory category,
            DelYN delYn,
            Pageable pageable
    );

    // 미읽음 카운트
    long countByUserAndDelYnAndReadIsFalse(User user, DelYN delYn);

    // 내 모든 알림 읽음 처리
    @Modifying
    @Query("update Notification n " +
            "set n.read = true " +
            "where n.user = :user " +
            "and n.read = false " +
            "and n.delYn = :delYn")
    int markAllReadByUser(@Param("user") User user,
                          @Param("delYn") DelYN delYn);


}
