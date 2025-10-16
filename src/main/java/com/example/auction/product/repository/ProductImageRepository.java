package com.example.auction.product.repository;

import com.example.auction.product.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    long countByProduct_ProductId(Long productId);

    List<ProductImage> findByProduct_ProductIdOrderByPositionAsc(Long productId);
    Optional<ProductImage> findByIdAndProduct_ProductId(Long imageId, Long productId);
}
