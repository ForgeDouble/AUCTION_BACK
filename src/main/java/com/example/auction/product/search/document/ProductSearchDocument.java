package com.example.auction.product.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchDocument {

    private Long productId;
    private Long categoryId;

    private String productName;
    private String productNameSearch;
    private String productContent;

    private Long price;
    private String status;

    private Boolean blocked;
    private Boolean delYn;

    private Long createdAtEpochMilli;
    private Long auctionEndAtEpochMilli;
}