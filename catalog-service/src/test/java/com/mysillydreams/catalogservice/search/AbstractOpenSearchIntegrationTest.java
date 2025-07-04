package com.mysillydreams.catalogservice.search;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.PostgreSQLContainer; // Keep if DB is also needed for these tests
import org.testcontainers.elasticsearch.ElasticsearchContainer; // Using ElasticsearchContainer for OpenSearch compatibility
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;


// This base class can be further specialized if needed
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE) // Or define specific properties for tests
@Testcontainers
@ContextConfiguration(initializers = AbstractOpenSearchIntegrationTest.OpenSearchInitializer.class)
public abstract class AbstractOpenSearchIntegrationTest {

    // If your search tests also interact with the database (e.g., indexer reads from DB)
    @Container
    protected static final PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>("postgres:15.3-alpine")
                    .withDatabaseName("test_catalog_search_db")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    // Use official OpenSearch image or compatible Elasticsearch one
    // For OpenSearch specific image:
    // DockerImageName openSearchImage = DockerImageName.parse("opensearchproject/opensearch:2.11.0");
    // @Container
    // protected static final OpenSearchContainer opensearchContainer = new OpenSearchContainer(openSearchImage);
    // For generic Elasticsearch container (often works for basic OpenSearch tests if version is aligned):
    @Container
    protected static final ElasticsearchContainer opensearchContainer =
            new ElasticsearchContainer(DockerImageName.parse("opensearchproject/opensearch:2.12.0")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
                    .withEnv("discovery.type", "single-node") // Important for OpenSearch
                    .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m"); // Adjust memory as needed


    public static class OpenSearchInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String opensearchHttpHostAddress = opensearchContainer.getHttpHostAddress();

            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    applicationContext,
                    "spring.datasource.url=" + postgresqlContainer.getJdbcUrl(),
                    "spring.datasource.username=" + postgresqlContainer.getUsername(),
                    "spring.datasource.password=" + postgresqlContainer.getPassword(),
                    "opensearch.uris=http://" + opensearchHttpHostAddress // Ensure scheme is included
            );
            // If your OpenSearch container uses auth, set username/password properties here too
            // "opensearch.username=user"
            // "opensearch.password=pass"
        }
    }
}
