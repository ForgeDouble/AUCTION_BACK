package com.example.auction.user.repository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.Authority;
import com.example.auction.user.domain.User;
import com.example.auction.user.dto.UserTokenDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.auction.user.dto.SellerDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);


    @Query("""
    select u
    from User u
    where u.delYn = :delYn
      and (:authority is null or u.authority = :authority)
      and (
           :keyword is null
        or lower(u.email) like lower(concat('%', :keyword, '%'))
        or lower(u.name) like lower(concat('%', :keyword, '%'))
        or lower(coalesce(u.nickname, '')) like lower(concat('%', :keyword, '%'))
      )
""")
    Page<User> searchUsersForAdmin(
            @Param("delYn") DelYN delYn,
            @Param("authority") Authority authority,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    long countByAuthorityAndDelYn(Authority authority, DelYN delYn);

    @Query("SELECT new com.example.auction.user.dto.SellerDto(" +
            "u.userId, u.email, u.nickname, u.profileImageUrl, u.createdAt, " +
            "COUNT(CASE WHEN p.status = 'SELLED' THEN 1 END)) " +
            "FROM Product p1 " +
            "JOIN p1.user u " +
            "LEFT JOIN Product p ON p.user.userId = u.userId AND p.status = 'SELLED' " +
            "WHERE p1.productId = :productId " +
            "GROUP BY u.userId, u.email, u.nickname, u.profileImageUrl, u.createdAt")
    Optional<SellerDto> findSellerInfoByProductId(@Param("productId") Long productId);

    @Query("SELECT new com.example.auction.user.dto.UserTokenDto(" +
            "u.email, u.authority) " +
            "FROM User u " +
            "WHERE u.email = :email")
    Optional<UserTokenDto> findUserTokenInfoByEmail(String email);
}
