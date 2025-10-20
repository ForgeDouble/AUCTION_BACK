package com.example.auction.product.domain;


import com.example.auction.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_image", indexes = {
        @Index(name="idx_product_image_product", columnList = "product_id"),
        @Index(name="idx_product_image_position", columnList = "product_id, position")
})
public class ProductImage extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="product_id")
    private Product product;

    @Column(nullable=false, length=500)
    private String s3Key;

    @Column(nullable=false, length=500)
    private String url;

    // 정렬 관련 컬럼
    @Column(nullable=false)
    private Integer position;
}
