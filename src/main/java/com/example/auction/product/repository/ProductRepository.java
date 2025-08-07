package com.example.auction.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auction.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
