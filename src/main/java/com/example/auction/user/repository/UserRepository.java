package com.example.auction.user.repository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndDelYn(String email, DelYN delyn);
    Optional<User> findByUserIdAndDelYn(Long userId, DelYN delyn);
    boolean existsByNicknameAndDelYn(String nickname, DelYN delYn);

    List<User> findAllByAuthorityAndDelYn(Authority authority, DelYN delYn);

    Optional<User> findFirstByAuthorityOrderByCreatedAtDesc(Authority authority);
    List<User> findAllByEmailInAndDelYn(List<String> emails, DelYN delYn);

    @Query("""
        select u.userId as userId,
               u.email as email,
               u.nickname as nickname,
               u.authority as authority,
               u.profileImageUrl as profileImageUrl
        from User u
        where u.email in :emails
          and u.delYn = :delYn
    """)
    List<UserSummaryProjection> findUserSummariesByEmails(@Param("emails") List<String> emails,
                                                          @Param("delYn") DelYN delYn);


    @Query("select count(u) from User u where u.createdAt >= :start and u.createdAt < :end and u.delYn = com.example.auction.common.domain.DelYN.N")
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
