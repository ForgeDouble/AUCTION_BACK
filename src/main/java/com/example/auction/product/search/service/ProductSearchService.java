package com.example.auction.product.search.service;

import com.example.auction.product.domain.Status;
import com.example.auction.product.search.document.ProductSearchDocument;
import com.example.auction.product.search.dto.ProductSearchIdsPageDto;
import com.example.auction.product.search.dto.ProductSearchRequest;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.index.product}")
    private String productIndex;

    public ProductSearchIdsPageDto searchProductIds(ProductSearchRequest req) {
        try {
            int from = req.getPage() * req.getSize();

            SearchResponse<ProductSearchDocument> response = openSearchClient.search(s -> {
                s.index(productIndex)
                        .from(from)
                        .size(req.getSize())
                        .trackTotalHits(t -> t.enabled(true))
                        .query(buildQuery(req));

                applySort(s, req.getSortBy());
                return s;
            }, ProductSearchDocument.class);

            long total = response.hits().total() != null ? response.hits().total().value() : 0L;

            List<Long> productIds = response.hits().hits().stream()
                    .map(hit -> hit.source() != null ? hit.source().getProductId() : Long.valueOf(hit.id()))
                    .toList();

            return new ProductSearchIdsPageDto(total, productIds);
        } catch (Exception e) {
            throw new RuntimeException("OpenSearch 상품 검색 중 오류가 발생했습니다.", e);
        }
    }

    private Query buildQuery(ProductSearchRequest req) {
        List<Query> filters = new ArrayList<>();

        filters.add(Query.of(q -> q.term(t -> t.field("blocked").value(FieldValue.of(false)))));
        filters.add(Query.of(q -> q.term(t -> t.field("delYn").value(FieldValue.of(false)))));

        if (req.getCategoryIds() != null && !req.getCategoryIds().isEmpty()) {
            List<FieldValue> values = req.getCategoryIds().stream()
                    .distinct()
                    .map(FieldValue::of)
                    .toList();

            filters.add(Query.of(q -> q.terms(t -> t
                    .field("categoryId")
                    .terms(v -> v.value(values))
            )));
        }

        if (req.getStatuses() != null && !req.getStatuses().isEmpty()) {
            List<FieldValue> values = req.getStatuses().stream()
                    .map(Status::name)
                    .distinct()
                    .map(FieldValue::of)
                    .toList();

            filters.add(Query.of(q -> q.terms(t -> t
                    .field("status")
                    .terms(v -> v.value(values))
            )));
        }

        if (req.getMinPrice() != null || req.getMaxPrice() != null) {
            filters.add(Query.of(q -> q.range(r -> {
                r.field("price");
                if (req.getMinPrice() != null) r.gte(JsonData.of(req.getMinPrice()));
                if (req.getMaxPrice() != null) r.lte(JsonData.of(req.getMaxPrice()));
                return r;
            })));
        }

        if (StringUtils.hasText(req.getSearchKeyword())) {
            return Query.of(q -> q.bool(b -> {
                b.filter(filters);
                b.should(s -> s.prefix(p -> p
                        .field("productNameSearch")
                        .value(req.getSearchKeyword())
                ));
                b.should(s -> s.multiMatch(mm -> mm
                        .query(req.getSearchKeyword())
                        .fields("productName^4", "productContent^2")
                ));
                b.minimumShouldMatch("1");
                return b;
            }));
        }

        return Query.of(q -> q.bool(b -> {
            b.filter(filters);
            b.must(m -> m.matchAll(ma -> ma));
            return b;
        }));
    }

    private void applySort(org.opensearch.client.opensearch.core.SearchRequest.Builder s, String sortBy) {
        String normalized = sortBy == null ? "NEWEST" : sortBy;

        switch (normalized) {
            case "ENDING_SOON" ->
                    s.sort(so -> so.field(f -> f.field("auctionEndAtEpochMilli").order(SortOrder.Asc)));
            case "NEWEST" ->
                    s.sort(so -> so.field(f -> f.field("createdAtEpochMilli").order(SortOrder.Desc)));
            default ->
                    s.sort(so -> so.field(f -> f.field("createdAtEpochMilli").order(SortOrder.Desc)));
        }
    }
}