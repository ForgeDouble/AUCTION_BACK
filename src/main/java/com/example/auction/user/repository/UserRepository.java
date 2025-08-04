package com.example.auction.user.repository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndDelYn(String email, DelYN delyn);
}
