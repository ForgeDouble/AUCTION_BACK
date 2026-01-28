package com.example.auction.product.repository;

import com.example.auction.product.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    long countByProduct_ProductId(Long productId);

    List<ProductImage> findByProduct_ProductIdOrderByPositionAsc(Long productId);
    Optional<ProductImage> findByIdAndProduct_ProductId(Long imageId, Long productId);

    interface ProductPreviewRow {
        Long getProductId();
        String getUrl();
        Integer getPosition();
    }

    @Query("""
        select pi.product.productId as productId, pi.url as url, pi.position as position
        from ProductImage pi
        where pi.product.productId in :productIds
        order by pi.product.productId asc, pi.position asc
    """)
    List<ProductPreviewRow> findPreviewRows(@Param("productIds") List<Long> productIds);
}
