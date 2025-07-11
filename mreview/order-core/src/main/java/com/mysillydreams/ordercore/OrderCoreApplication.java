package com.mysillydreams.ordercore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories; // Good practice if repositories are in different sub-packages
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Required for @Scheduled tasks like OutboxPoller
@EnableJpaRepositories(basePackages = "com.mysillydreams.ordercore.repository") // Explicitly scan for JPA repos
// ComponentScan might be needed if some components are outside the main package structure,
// but with standard layout, SpringBootApplication usually covers it.
// @ComponentScan(basePackages = {"com.mysillydreams.ordercore"})
public class OrderCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderCoreApplication.class, args);
	}

}
