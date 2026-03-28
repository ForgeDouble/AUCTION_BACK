//package com.example.auction.product.search.service;
//
//import com.example.auction.product.domain.Product;
//import com.example.auction.product.repository.ProductRepository;
//import com.example.auction.product.search.document.ProductSearchDocument;
//import lombok.extern.slf4j.Slf4j;
//import org.opensearch.client.opensearch.OpenSearchClient;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.stereotype.Service;
//
//@Service
//@Slf4j
//@ConditionalOnProperty(name = "opensearch.enabled", havingValue = "true")
//public class ProductSearchIndexService {
//
//    private final OpenSearchClient openSearchClient;
//    private final ProductRepository productRepository;
//    private final ProductSearchDocumentMapper productSearchDocumentMapper;
//
//    @Value("${opensearch.index.product}")
//    private String productIndex;
//
//    public ProductSearchIndexService(OpenSearchClient openSearchClient, ProductRepository productRepository, ProductSearchDocumentMapper productSearchDocumentMapper) {
//        this.openSearchClient = openSearchClient;
//        this.productRepository = productRepository;
//        this.productSearchDocumentMapper = productSearchDocumentMapper;
//    }
//
//    public void upsertByProductId(Long productId) {
//        try {
//            Product product = productRepository.findById(productId)
//                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. productId=" + productId));
//
//            ProductSearchDocument doc = productSearchDocumentMapper.toDocument(product);
//
//            openSearchClient.index(i -> i
//                    .index(productIndex)
//                    .id(String.valueOf(productId))
//                    .document(doc)
//            );
//
//            log.info("[OpenSearch] 상품 인덱싱 완료 productId={}", productId);
//        } catch (Exception e) {
//            log.error("[OpenSearch] 상품 인덱싱 실패 productId={}", productId, e);
//            throw new RuntimeException("상품 인덱싱 중 오류가 발생했습니다.");
//        }
//    }
//}