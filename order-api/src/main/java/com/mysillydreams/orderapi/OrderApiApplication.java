package com.mysillydreams.orderapi;

import com.mysillydreams.orderapi.service.InMemoryIdempotencyService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.annotation.PreDestroy;

@SpringBootApplication
public class OrderApiApplication {

	private static ConfigurableApplicationContext context;

	public static void main(String[] args) {
		context = SpringApplication.run(OrderApiApplication.class, args);
	}

	// Graceful shutdown for in-memory idempotency service (if used)
	@PreDestroy
	public void onExit() {
		if (context != null) {
			InMemoryIdempotencyService idempotencyService = context.getBeanProvider(InMemoryIdempotencyService.class).getIfAvailable();
			if (idempotencyService != null) {
				idempotencyService.shutdown();
				System.out.println("In-memory idempotency service scheduler shut down.");
			}
		}
	}
}
