package com.mysillydreams.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component; // Or use @Configuration
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Component // Makes it a Spring bean, eligible for DI
@ConfigurationProperties(prefix = "app.commission")
@Data // Lombok for getters/setters
@Validated // Enable validation on these properties
public class CommissionProperties {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true, message = "Commission percent must be non-negative.")
    @DecimalMax(value = "100.0", inclusive = true, message = "Commission percent cannot exceed 100.")
    private BigDecimal percent; // e.g., 10.0 for 10%
}
