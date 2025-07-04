package com.mysillydreams.catalogservice.service.search;

import com.mysillydreams.catalogservice.config.OpenSearchConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.ExistsRequest;
import org.opensearch.client.indices.IndexSettings;
import org.opensearch.client.indices.analyze.Analyzer;
import org.opensearch.client.indices.analyze.Tokenizer;
import org.opensearch.client.java.OpenSearchIndicesClient;
import org.opensearch.client.java.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.IndexSettingsAnalysis;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSearchIndexInitializer {

    private final OpenSearchClient openSearchClient;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        try {
            OpenSearchIndicesClient indicesClient = openSearchClient.indices();
            boolean indexExists = indicesClient.exists(new ExistsRequest.Builder().index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME).build()).value();

            if (!indexExists) {
                log.info("Index '{}' does not exist. Creating with mapping...", OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME);
                createIndexWithMapping(indicesClient);
            } else {
                log.info("Index '{}' already exists.", OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME);
                // Optionally, update mapping if necessary and safe (can be complex)
            }
        } catch (IOException e) {
            log.error("Failed to initialize OpenSearch index '{}': {}", OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME, e.getMessage(), e);
            // Depending on policy, might want to throw an exception to halt startup if search is critical
        }
    }

    private void createIndexWithMapping(OpenSearchIndicesClient indicesClient) throws IOException {
        Map<String, Property> properties = new HashMap<>();
        properties.put("id", Property.of(p -> p.keyword(k -> k.index(true)))); // UUID as keyword
        properties.put("sku", Property.of(p -> p.keyword(k -> k.index(true))));
        properties.put("name", Property.of(p -> p.text(t -> t.analyzer("standard").fields("keyword", Property.of(f -> f.keyword(k -> k.ignoreAbove(256)))))));
        properties.put("description", Property.of(p -> p.text(t -> t.analyzer("standard"))));
        properties.put("itemType", Property.of(p -> p.keyword(k -> k.index(true)))); // PRODUCT, SERVICE
        properties.put("basePrice", Property.of(p -> p.double_(d -> d))); // Or scaled_float
        properties.put("active", Property.of(p -> p.boolean_(b -> b)));
        properties.put("createdAt", Property.of(p -> p.date(d -> d)));
        properties.put("updatedAt", Property.of(p -> p.date(d -> d)));
        properties.put("categoryId", Property.of(p -> p.keyword(k -> k.index(true))));
        properties.put("categoryName", Property.of(p -> p.text(t -> t.fields("keyword", Property.of(f -> f.keyword(k -> k.ignoreAbove(256)))))));
        // Use 'keyword' for exact path matching or a custom analyzer for path hierarchy search
        properties.put("categoryPathKeyword", Property.of(p -> p.keyword(k -> k.index(true)))); // For exact path or prefix queries
        properties.put("categoryPathHierarchy", Property.of(p -> p.text(t -> t.analyzer("path_hierarchy_analyzer")))); // For path hierarchy search

        // Example for metadata (if it has known structure, map it, otherwise 'object' or 'flattened')
        // properties.put("metadata", Property.of(p -> p.object(o -> o.enabled(true).dynamic(true)))); // Allows dynamic fields in metadata
        // A 'flattened' type might be better if metadata structure is very diverse and not queried directly often
        properties.put("metadata_flattened", Property.of(p -> p.flattened(f -> f)));


        TypeMapping mapping = TypeMapping.of(tm -> tm.properties(properties));

        // Define custom analyzer for path hierarchy
        IndexSettingsAnalysis analysis = IndexSettingsAnalysis.of(isa -> isa
            .analyzer("path_hierarchy_analyzer", Analyzer.of(a -> a
                .custom(ca -> ca
                    .tokenizer("path_hierarchy_tokenizer")
                // You might add filters like lowercase here if paths are case-insensitive
                )))
            .tokenizer("path_hierarchy_tokenizer", Tokenizer.of(t -> t
                .definition(td -> td
                    .type("path_hierarchy")
                    .put("delimiter", "/")
                )))
        );

        IndexSettings settings = IndexSettings.of(is -> is.analysis(analysis));

        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder()
                .index(OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME)
                .mappings(mapping)
                .settings(settings)
                .build();

        indicesClient.create(createIndexRequest);
        log.info("Index '{}' created successfully with mapping.", OpenSearchConfig.CATALOG_ITEMS_INDEX_NAME);
    }
}
