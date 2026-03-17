package com.example.auction.product.search.listener;

import com.example.auction.product.search.ProductIndexEvent;
import com.example.auction.product.search.service.ProductSearchIndexService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductSearchIndexListener {
    private final ProductSearchIndexService productSearchIndexService;

    public ProductSearchIndexListener(ProductSearchIndexService productSearchIndexService) {
        this.productSearchIndexService = productSearchIndexService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductIndexEvent event) {
        productSearchIndexService.upsertByProductId(event.productId());
    }
}