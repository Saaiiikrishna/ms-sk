package com.mysillydreams.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.VaultToken;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultConfig {

    @Value("${spring.cloud.vault.uri:http://vault:8200}")
    private String vaultUri;

    @Value("${spring.cloud.vault.token:root-token}")
    private String vaultToken;

    @Value("${spring.cloud.vault.transit.backend:transit}")
    private String transitBackend;

    @Bean
    public VaultTemplate vaultTemplate() {
        try {
            VaultEndpoint vaultEndpoint = VaultEndpoint.from(URI.create(vaultUri));
            VaultToken token = VaultToken.of(vaultToken);
            TokenAuthentication authentication = new TokenAuthentication(token);
            
            return new VaultTemplate(vaultEndpoint, authentication);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create VaultTemplate", e);
        }
    }

    @Bean
    public VaultTransitOperations vaultTransitOperations(VaultTemplate vaultTemplate) {
        try {
            return vaultTemplate.opsForTransit(transitBackend);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create VaultTransitOperations", e);
        }
    }
}
