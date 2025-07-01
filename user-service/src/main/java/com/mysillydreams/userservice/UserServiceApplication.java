package com.mysillydreams.userservice;

import com.mysillydreams.userservice.converter.CryptoConverter; // Import to reference
import com.mysillydreams.userservice.service.EncryptionService; // Import to reference
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing; // For @CreationTimestamp etc.

@SpringBootApplication
@EnableJpaAuditing // To enable @CreationTimestamp and @UpdateTimestamp in entities
public class UserServiceApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(UserServiceApplication.class, args);

        // Manual check/trigger for CryptoConverter static injection if needed,
        // though @Component on CryptoConverter and @Autowired on its setter should handle it.
        // This is more of a diagnostic step or a fallback if issues are seen with the static injection.
        // EncryptionService encryptionService = context.getBean(EncryptionService.class);
        // CryptoConverter cryptoConverter = context.getBean(CryptoConverter.class); // If it's a bean
        // cryptoConverter.setEncryptionService(encryptionService); // This would be redundant if @Autowired setter works
    }

    // Optional: Define a Spring Cloud Vault specific health indicator if needed for more detailed Vault health.
    // By default, Spring Boot Actuator includes a general Vault health indicator if spring-cloud-starter-vault-config is present.
    /*
    @Bean
    public HealthIndicator vaultHealthIndicator(VaultOperations vaultOperations) {
        // Simple check if Vault is accessible
        return () -> {
            try {
                vaultOperations.opsForSys().health(); // Check Vault's health endpoint
                return Health.up().withDetail("vault", "Accessible").build();
            } catch (Exception e) {
                return Health.down().withDetail("vault", "Not accessible").withException(e).build();
            }
        };
    }
    */

}
