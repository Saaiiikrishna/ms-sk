package com.ecommerce.vendorfulfillmentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VendorFulfillmentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VendorFulfillmentServiceApplication.class, args);
	}

}
