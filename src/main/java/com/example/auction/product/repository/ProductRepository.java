package com.example.auction.product.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.auction.product.domain.SellYN;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
	Optional<Product> findByProductIdAndDelYn(Long productId, DelYN delYN);
    List<Product> findBySellYNAndCreatedAtBefore(SellYN sellYN, LocalDateTime createdBefore);

	Optional<Product> findByProductIdAndDelYnAndBlocked(Long productId, DelYN delYn, Boolean blocked);
	List<Product> findByBlockedAndDelYn(Boolean blocked, DelYN delYn);
}
