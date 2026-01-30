package com.example.auction.user.repository;

import com.example.auction.user.domain.PasswordResetToken;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.isUsed = true WHERE p.user.userId = :userId AND p.isUsed = false")
    void invalidateUserTokens(@Param("userId") Long userId);
}
