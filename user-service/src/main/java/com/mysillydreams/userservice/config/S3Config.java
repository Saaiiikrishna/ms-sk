package com.mysillydreams.userservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Optional;

@Configuration
public class S3Config {

    private static final Logger logger = LoggerFactory.getLogger(S3Config.class);

    @Value("${vendor.s3.region:#{null}}") // Default to null if not set, SDK will try to determine
    private Optional<String> s3Region;

    @Value("${vendor.s3.endpoint-override:#{null}}")
    private Optional<String> s3EndpointOverride;

    @Bean
    public S3Client s3Client() {
        S3Client.Builder builder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create()); // Uses default chain

        s3Region.ifPresent(region -> {
            logger.info("Configuring S3 client with region: {}", region);
            builder.region(Region.of(region));
        });

        s3EndpointOverride.ifPresent(endpoint -> {
            logger.info("Configuring S3 client with endpoint override: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint));
            // For LocalStack or MinIO, you might also need to enable path-style access:
            // builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
            // This requires S3Configuration, which might be S3Configuration.builder().pathStyleAccessEnabled(true).build()
            // This needs to be set on S3Client not S3Presigner directly.
        });

        logger.info("S3Client bean created. Region: {}, Endpoint Override: {}",
            s3Region.orElse("SDK Default"), s3EndpointOverride.orElse("None"));

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Client s3Client) { // S3Client will be injected here
        S3Presigner.Builder builder = S3Presigner.builder()
                .credentialsProvider(DefaultCredentialsProvider.create()); // Presigner also needs credentials

        // Region for presigner should match the S3Client's region for the bucket
        s3Region.ifPresent(region -> {
            logger.info("Configuring S3 presigner with region: {}", region);
            builder.region(Region.of(region));
        });

        s3EndpointOverride.ifPresent(endpoint -> {
             logger.info("Configuring S3 presigner with endpoint override: {}", endpoint);
            builder.endpointOverride(URI.create(endpoint));
        });

        // If S3Client was configured with pathStyleAccessEnabled, presigner might need similar awareness
        // but usually this is handled at S3Client level.
        // Presigner uses the S3Client's configuration implicitly if not overridden.
        // For more direct control, pass s3Client.serviceConfiguration() if needed.

        logger.info("S3Presigner bean created. Region: {}, Endpoint Override: {}",
            s3Region.orElse("SDK Default"), s3EndpointOverride.orElse("None"));

        return builder.build();
    }
}
