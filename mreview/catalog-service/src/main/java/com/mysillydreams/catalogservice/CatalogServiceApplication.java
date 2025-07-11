package com.mysillydreams.catalogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling; // Added for @Scheduled

@SpringBootApplication
@EnableRetry // Enable Spring Retry capabilities
@EnableScheduling // Enable @Scheduled tasks
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

}
