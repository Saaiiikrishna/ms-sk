package com.mysillydreams.catalogservice.service.search;

import com.mysillydreams.catalogservice.config.OpenSearchConfig;
import com.mysillydreams.catalogservice.domain.model.ItemType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.java.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final OpenSearchClient openSearchClient;
    private final MeterRegistry meterRegistry; // Added MeterRegistry

    // Define a timer for search latency
    private static final String SEARCH_LATENCY_METRIC_NAME = "catalog.search.latency";
    private static final String TAG_SEARCH_TYPE = "search_type";


    public Page<CatalogItemSearchDocument> searchItems(
            String keywordQuery,
            UUID categoryId, // Filter by specific category ID
            String categoryPath, // Filter by path prefix (for searching within a category and its descendants)
            ItemType itemType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        log.debug("Searching items with keyword: '{}', categoryId: {}, categoryPath: {}, itemType: {}, minPrice: {}, maxPrice: {}, pageable: {}",
                keywordQuery, categoryId, categoryPath, itemType, minPrice, maxPrice, pageable);

        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();

        // Keyword search (name, description, SKU)
        if (StringUtils.hasText(keywordQuery)) {
            boolQueryBuilder.must(QueryBuilders.multiMatch(m -> m
                    .query(keywordQuery)
                    .fields("name^3", "sku^2", "description", "categoryName") // Boost name and SKU
                    .type(TextQueryType.BestFields)
            ));
        } else {
             boolQueryBuilder.must(QueryBuilders.matchAll(m -> m)); // Match all if no keyword
        }

        // Filters
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.term(t -> t.field("active").value(true)))); // Only active items

        if (categoryId != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("categoryId").value(categoryId.toString()))));
        }

        if (StringUtils.hasText(categoryPath)) {
            // Use categoryPathKeyword for prefix search on the materialized path
            filters.add(Query.of(q -> q.prefix(p -> p.field("categoryPathKeyword").value(categoryPath))));
        }

        if (itemType != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("itemType").value(itemType.name()))));
        }

        RangeQuery.Builder priceRangeQueryBuilder = QueryBuilders.range().field("basePrice");
        boolean priceFilterExists = false;
        if (minPrice != null) {
            priceRangeQueryBuilder.gte(jsonValue -> jsonValue.doubleValue(minPrice.doubleValue()));
            priceFilterExists = true;
        }
        if (maxPrice != null) {
            priceRangeQueryBuilder.lte(jsonValue -> jsonValue.doubleValue(maxPrice.doubleValue()));
            priceFilterExists = true;
        }
        if (priceFilterExists) {
            filters.add(Query.of(q -> q.range(priceRangeQueryBuilder.build())));
        }

        boolQueryBuilder.filter(filters);

        SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
                .index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME)
                .query(q -> q.bool(boolQueryBuilder.build()))
                .from((int) pageable.getOffset())
                .size(pageable.getPageSize());


        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                String property = order.getProperty();
                // Ensure .keyword is appended for text fields intended for sorting/faceting if mapping requires it
                if ("name".equals(property) || "categoryName".equals(property) || "sku".equals(property)) {
                    property += ".keyword";
                }
                final String finalProperty = property; // Effectively final for lambda
                searchRequestBuilder.sort(s -> s.field(f -> f
                        .field(finalProperty)
                        .order(order.isAscending() ? org.opensearch.client.opensearch._types.SortOrder.Asc : org.opensearch.client.opensearch._types.SortOrder.Desc)
                ));
            });
        } else {
             // Default sort by relevance if no sort specified in pageable
            searchRequestBuilder.sort(s -> s.field(f -> f.field("_score").order(org.opensearch.client.opensearch._types.SortOrder.Desc)));
        }


        Timer.Sample DUMMY = Timer.start(meterRegistry); // Start timer before the operation
        try {
            SearchResponse<CatalogItemSearchDocument> response = openSearchClient.search(searchRequestBuilder.build(), CatalogItemSearchDocument.class);

            List<CatalogItemSearchDocument> documents = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            DUMMY.stop(meterRegistry.timer(SEARCH_LATENCY_METRIC_NAME, TAG_SEARCH_TYPE, "item_search")); // Stop timer and record

            return new PageImpl<>(documents, pageable, totalHits);

        } catch (IOException e) {
            log.error("Error searching OpenSearch for query '{}': {}", keywordQuery, e.getMessage(), e);
            // Record error metric if desired
            meterRegistry.counter("catalog.search.errors", TAG_SEARCH_TYPE, "item_search").increment();
            DUMMY.stop(meterRegistry.timer(SEARCH_LATENCY_METRIC_NAME, TAG_SEARCH_TYPE, "item_search_error")); // Stop timer with error tag
            return Page.empty(pageable);
        }
    }
}
