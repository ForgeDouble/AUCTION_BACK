package com.example.auction.product.search.service;

import com.example.auction.common.domain.DelYN;
import com.example.auction.product.domain.Product;
import com.example.auction.product.search.document.ProductSearchDocument;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class ProductSearchDocumentMapper {

    public ProductSearchDocument toDocument(Product product) {
        return ProductSearchDocument.builder()
                .productId(product.getProductId())
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .productName(product.getProductName())
                .productNameSearch(product.getProductNameSearch())
                .productContent(product.getProductContent())
                .price(product.getPrice())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .blocked(Boolean.TRUE.equals(product.getBlocked()))
                .delYn(product.getDelYn() == DelYN.Y)
                .createdAtEpochMilli(
                        product.getCreatedAt() != null
                                ? product.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                : null
                )
                .auctionEndAtEpochMilli(
                        product.getAuctionEndTime() != null
                                ? product.getAuctionEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                : null
                )
                .build();
    }
}