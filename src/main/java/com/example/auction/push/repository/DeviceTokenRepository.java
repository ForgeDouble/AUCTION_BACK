package com.example.auction.push.repository;

import com.example.auction.push.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

}
