package com.example.auction.product.search.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ProductSearchIndexInitializer {

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.index.product}")
    private String productIndex;

    @Bean
    public ApplicationRunner productIndexRunner() {
        return args -> {
            try {
                boolean exists = openSearchClient.indices()
                        .exists(e -> e.index(productIndex))
                        .value();

                if (exists) {
                    log.info("[OpenSearch] product index already exists: {}", productIndex);
                    return;
                }

                openSearchClient.indices().create(c -> c
                        .index(productIndex)
                        .mappings(m -> m
                                .properties("productId", p -> p.long_(l -> l))
                                .properties("categoryId", p -> p.long_(l -> l))
                                .properties("productName", p -> p.text(t -> t))
                                .properties("productNameSearch", p -> p.keyword(k -> k.ignoreAbove(256)))
                                .properties("productContent", p -> p.text(t -> t))
                                .properties("price", p -> p.long_(l -> l))
                                .properties("status", p -> p.keyword(k -> k))
                                .properties("blocked", p -> p.boolean_(b -> b))
                                .properties("delYn", p -> p.boolean_(b -> b))
                                .properties("createdAtEpochMilli", p -> p.long_(l -> l))
                                .properties("auctionEndAtEpochMilli", p -> p.long_(l -> l))
                        )
                );

                log.info("[OpenSearch] index created: {}", productIndex);

            } catch (Exception e) {
                log.warn("[OpenSearch] 서버 연결 실패로 인덱스 초기화를 건너뜁니다. host/port 확인 필요", e);
            }
        };
    }
}