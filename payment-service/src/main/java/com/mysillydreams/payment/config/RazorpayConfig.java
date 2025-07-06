package com.mysillydreams.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RazorpayConfig {

    @Value("${payment.razorpay.key-id}")
    private String keyId;

    @Value("${payment.razorpay.key-secret}")
    private String keySecret;

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        if (!StringUtils.hasText(keyId) || !StringUtils.hasText(keySecret)) {
            // This is a critical configuration. Fail fast if not provided.
            // Alternatively, could return a dummy/mock client or not create the bean
            // if a profile indicates Razorpay is disabled (e.g., for certain tests or local dev without creds).
            // However, for a payment service, these are usually essential.
            throw new IllegalStateException("Razorpay keyId and keySecret must be configured.");
        }
        // The RazorpayClient constructor can throw RazorpayException, though typically it's for API calls.
        // It's good practice to declare it.
        return new RazorpayClient(keyId, keySecret);
    }
}
