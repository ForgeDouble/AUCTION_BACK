package com.example.auction.product.repository;

import com.example.auction.product.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    long countByProduct_ProductId(Long productId);
}
