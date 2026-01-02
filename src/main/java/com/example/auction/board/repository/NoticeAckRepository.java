package com.example.auction.board.repository;

import com.example.auction.board.domain.NoticeAck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoticeAckRepository extends JpaRepository<NoticeAck, Long> {

    boolean existsByNotice_IdAndUser_UserId(Long noticeId, Long userId);

    @Query("select na.notice.id from NoticeAck na " +
            "where na.user.userId = :userId and na.notice.id in :noticeIds")
    List<Long> findAckedNoticeIds(@Param("userId") Long userId, @Param("noticeIds") List<Long> noticeIds);
}
