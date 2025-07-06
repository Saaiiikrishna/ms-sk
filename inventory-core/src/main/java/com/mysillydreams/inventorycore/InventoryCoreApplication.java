package com.mysillydreams.inventorycore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Required for @Scheduled tasks like OutboxPoller
public class InventoryCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryCoreApplication.class, args);
    }

}
