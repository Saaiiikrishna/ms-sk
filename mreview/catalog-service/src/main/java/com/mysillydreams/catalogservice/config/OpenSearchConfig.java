package com.mysillydreams.catalogservice.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.java.OpenSearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.uris}")
    private String uris; // Comma-separated: http://localhost:9200

    @Value("${opensearch.username:#{null}}") // Optional username
    private String username;

    @Value("${opensearch.password:#{null}}") // Optional password
    private String password;

    @Bean
    public OpenSearchClient openSearchClient() {
        HttpHost[] httpHosts = parseUris(uris);

        RestClientBuilder restClientBuilder = RestClient.builder(httpHosts);

        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            restClientBuilder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        // For HTTPS, you might need to configure SSL context:
        // restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setSSLContext(...));


        RestClient restClient = restClientBuilder.build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new OpenSearchClient(transport);
    }

    private HttpHost[] parseUris(String urisString) {
        if (!StringUtils.hasText(urisString)) {
            throw new IllegalArgumentException("OpenSearch URIs must be configured.");
        }
        String[] uriArray = urisString.split(",");
        HttpHost[] httpHostsArray = new HttpHost[uriArray.length];
        for (int i = 0; i < uriArray.length; i++) {
            httpHostsArray[i] = HttpHost.create(uriArray[i].trim());
        }
        return httpHostsArray;
    }

    // Define your index name as a constant or from properties
    public static final String CATALOG_ITEMS_INDEX_NAME = "catalog-items";
}
