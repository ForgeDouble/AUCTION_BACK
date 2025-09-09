package com.example.auction.push.repository;

import com.example.auction.push.domain.DeviceToken;
import com.example.auction.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);
    List<DeviceToken> findAllByUser_UserIdAndValidTrue(Long userId);
    List<DeviceToken> findAllByUser(User user);
    void deleteByToken(String token);
}
