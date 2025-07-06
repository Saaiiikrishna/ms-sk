package com.mysillydreams.orderapi;

// No longer needed: import com.mysillydreams.orderapi.service.InMemoryIdempotencyService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// No longer needed: import org.springframework.context.ConfigurableApplicationContext;
// No longer needed: import javax.annotation.PreDestroy;

@SpringBootApplication
public class OrderApiApplication {

	// No longer needed: private static ConfigurableApplicationContext context;

	public static void main(String[] args) {
		// context = SpringApplication.run(OrderApiApplication.class, args);
		SpringApplication.run(OrderApiApplication.class, args);
	}

	// Graceful shutdown for in-memory idempotency service is no longer needed
	// @PreDestroy
	// public void onExit() {
	// 	if (context != null) {
	// 		InMemoryIdempotencyService idempotencyService = context.getBeanProvider(InMemoryIdempotencyService.class).getIfAvailable();
	// 		if (idempotencyService != null) {
	// 			idempotencyService.shutdown();
	// 			System.out.println("In-memory idempotency service scheduler shut down.");
	// 		}
	// 	}
	// }
}
